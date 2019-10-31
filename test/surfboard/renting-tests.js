
const lib = require('./lib.js')

const gracePeriod = 5      //   7 * 1440 =   7 days
const interestPeriod = 4   // 365 * 1440 = 365 days
const burndownPeriod = 40   // 365 * 1440 = 365 days
const initialOracleRate = 10000
const discount = 80 // should lend only 80% of rate
const alicePmt = 10 * 1000 * lib.u1

describe('user test suite', async function () {

    this.timeout(100000);
    var tokenId = null

    var bobHeight = null
    var cooperHeight = null

    before(async function () {
        await setupAccounts(
            {
                tokenIssuer: 2 * lib.u1,
                owner: 2 * lib.u1,
                oracle: 1 * lib.u1,
                dapp: 2 * lib.u1,
                alice: 30 * 1000 * lib.u1,
                bob: 30 * 1000 * lib.u1,
                cooper: 100 * 1000 * lib.u1,
                ivanov: 1 * lib.u1,
            });
    });

    it('issuer issues a token', async function () {
        const tx = issue({ name: "SUPERBTC", description: "Gateway-backed BTC", quantity: 24 * 1000 * 1000 * lib.u1, decimals: 8 }, accounts.tokenIssuer)
        await broadcast(tx)
        await waitForTx(tx.id)
        tokenId = tx.id

    })

    it('issuer sends 11 BTC to ivanov, 1 to Alice', async function () {
        const tx = massTransfer(
            {
                transfers: [{ amount: 11 * lib.u1, recipient: address(accounts.ivanov) },
                { amount: 1 * lib.u1, recipient: address(accounts.alice) },
                { amount: 1 * lib.u1, recipient: address(accounts.bob) }
                ], assetId: tokenId
            }, accounts.tokenIssuer)
        await broadcast(tx)
        await waitForTx(tx.id)
    })

    it('dapp deploys script', async function () {
        const tx = setScript({ script: compile(file("pawnshop_wbtc.ride")) }, accounts.dapp)
        await broadcast(tx)
        await waitForTx(tx.id)
    })

    it('dapp sets token and oracle', async function () {
        console.log("token: " + tokenId)
        const tx = invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "init",
                args: [
                    { type: "string", value: address(accounts.owner) },
                    { type: "string", value: tokenId },
                    { type: "string", value: address(accounts.oracle) },
                    { type: "integer", value: 50000 },
                    { type: "integer", value: discount },
                    { type: "integer", value: gracePeriod },
                    { type: "integer", value: interestPeriod },
                    { type: "integer", value: burndownPeriod },
                    { type: "integer", value: 10 }, // 10%
                    { type: "integer", value: 10 } 
                ]
            },
            payment: [],
            additionalFee: 400000
        },
            accounts.dapp
        )
        await broadcast(tx)
        await waitForTx(tx.id)
    })

    it('oracle sets rate', async function () { await lib.postOracle(accounts.oracle, initialOracleRate) })

    it('ivanov becomes lender', async function() {
        await lib.depositBtc(accounts.ivanov, tokenId)
    })

    it('Alice borrows some btc 10k waves', async function () {
        const tx = await lib.borrow(accounts.alice, alicePmt)
        const sc = await stateChanges(tx.id)
        const aliceAddress = address(accounts.alice)
        const [so, eog, eoi, eob, r, d, l] = lib.parseData(sc.data, aliceAddress)
        expect(l).to.equal(alicePmt * initialOracleRate / lib.u1 * discount / 100)
        expect(eog - so).to.equal(gracePeriod)
        expect(eoi - eog).to.equal(interestPeriod)
        expect(eob - eog).to.equal(burndownPeriod)
        expect(r).to.equal(initialOracleRate * discount / 100)
        expect(d).to.equal(alicePmt)

        const transfer = sc.transfers[0]
        expect(transfer.address).to.equal(address(accounts.alice))
        expect(transfer.asset).to.equal(tokenId)
        expect(transfer.amount).to.equal(l)
    })
    it('Bob borrows some btc for 5k waves', async function () {
        const tx = await lib.borrow(accounts.bob, 5 * 1000 * lib.u1);
        bobHeight = await currentHeight()
    })

    it('Cooper borrows some btc for 3k waves', async function () {
        const tx = await lib.borrow(accounts.cooper, 3 * 1000 * lib.u1);
        cooperHeight = await currentHeight()
    })

    it('Alice cant buy back with less tokens', async function () {
        const bad1 = lib.buildBuyBackTx(accounts.alice, 0.8 * lib.u1, null)
        await expect(broadcast(bad1)).to.be.rejectedWith("Error")
    })

    it('Alice cant buy back with different token', async function () {
        const bad2 = lib.buildBuyBackTx(accounts.alice, 0.8 * lib.u1 - 1, tokenId)
        await expect(broadcast(bad2)).to.be.rejectedWith("User must return")
    })

    it('Alice buys back 10k waves for 0.8 btc that has been borrowed', async function () {
        this.timeout(20000)
        const tx = lib.buildBuyBackTx(accounts.alice, 0.8 * lib.u1, tokenId)
        await broadcast(tx)
        await waitForTx(tx.id)

        const aliceAddress = address(accounts.alice)
        const sc = await stateChanges(tx.id)
        const transfer = sc.transfers[0]

        const [so, eog, eoi, eob, r, d, l] = lib.parseData(sc.data, aliceAddress)

        expect(eog).to.equal(0)
        expect(eoi).to.equal(0)
        expect(eob).to.equal(0)
        expect(so).to.equal(0)
        expect(r).to.equal(0)
        expect(d).to.equal(0)
        expect(l).to.equal(0)

        expect(transfer.address).to.equal(address(accounts.alice))
        expect(transfer.asset).to.equal(null)
        expect(transfer.amount).to.equal(alicePmt)

    })



    it('Alice cant buyback again', async function () {
        const tx = lib.buildBuyBackTx(accounts.alice, 0.8 * lib.u1, tokenId)
        await expect(broadcast(tx)).to.be.rejectedWith("No open loan")
    })

    it('fails to borrow if rate is suspicious', async function () {
        await lib.postOracle(accounts.oracle, 50001)
        const attempt = lib.buildBorrowTx(accounts.alice, 2 * 1000 * lib.u1)
        await expect(broadcast(attempt)).to.be.rejectedWith("Suspicious")
    })

    it('reset back to reasonable rate', async function () {
        await lib.postOracle(accounts.oracle, 20000)
    })

    it('Alice can perform the operation again', async function () {
        await lib.borrow(accounts.alice, 2 * 1000 * lib.u1)
        const toBuyBack = 2000 * 20000 * 0.8
        const bb = lib.buildBuyBackTx(accounts.alice, toBuyBack, tokenId)
        await broadcast(bb)
        await waitForTx(bb.id)
    })

    it('Bob buys back after grace period ends, pays more', async function () {
        this.timeout(2 * 1000 * 1000)
        const h = bobHeight + gracePeriod + 2
        console.log("Bob tx height: " + bobHeight)
        console.log("Required height: " + h)
        await waitForHeight(h, { timeout: 2 * 1000 * 1000 })
        console.log("Actual height: " + await (currentHeight()))
        const tx = invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "buyBack", args: []
            },
            payment: [{ amount: 0.4 * lib.u1 + 0.1*lib.u1, assetId: tokenId }]
        },
            accounts.bob
        )
        await broadcast(tx)
        await waitForTx(tx.id)
        const sc = await stateChanges(tx.id)
        const t0 = await transactionById(tx.id) // just to query real height
        const actualHeight = t0.height
        const expectedProfit =  0.4 * lib.u1 * (actualHeight - (bobHeight+gracePeriod))/burndownPeriod

        const [profit, unclaimed] = lib.parsePayouts(sc.data, address(accounts.ivanov))

        expect(profit).to.equal(expectedProfit * 0.9)
        expect(unclaimed).to.equal(null)
        
        const [profitOwner, unclaimedOwner] = lib.parsePayouts(sc.data, address(accounts.owner))
       expect(profitOwner).to.equal(expectedProfit * 0.1)
       expect(unclaimedOwner).to.equal(null)
    })

    it('Cooper doesnt buy back during interest period, owner makes closeExpiredFor', async function () {
        this.timeout(2 * 1000 * 1000)
        const h = cooperHeight + gracePeriod + interestPeriod + 2
        console.log("Cooper tx height: " + bobHeight)
        console.log("Required height: " + h)
        await waitForHeight(h, { timeout: 2 * 1000 * 1000 })
        console.log("Actual height: " + await (currentHeight()))
        const tx = invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "closeExpiredFor", args: [{ type: "string", value: address(accounts.cooper) }]
            }
        },
            accounts.owner
        )
        await broadcast(tx)
        await waitForTx(tx.id)

        const sc = await stateChanges(tx.id)

        const [profit, unclaimed] = lib.parsePayouts(sc.data, address(accounts.ivanov))
        expect(profit).to.equal(null)
        expect(unclaimed).to.equal(3 * lib.u1 * 1000 * 0.9)
        
        const [profitOwner, unclaimedOwner] = lib.parsePayouts(sc.data, address(accounts.owner))
        expect(profitOwner).to.equal(null)
        expect(unclaimedOwner).to.equal(3 * lib.u1 * 1000 * 0.1)
        const expectedCurculatingAssets = 10*lib.u1 - 24000000
        expect(sc.data.find(k => k.key == "curculating_assets_of_" + address(accounts.ivanov)).value)
            .to
            .equal((expectedCurculatingAssets))
    })

    it('ivanov can take profit', async function () {
        const tx = await lib.takeProfit(accounts.ivanov)
        const sc = await stateChanges(tx.id)
        console.log(sc)
    })

    it('owner can take profit ', async function () {
        const tx = await lib.takeProfit(accounts.owner)
        const sc = await stateChanges(tx.id)
        console.log(sc)
    })
})