const lib = require('./lib.js')

describe('rates test suite', async function () {
    var tokenId = null
    before(async function () {
        await setupAccounts(
            {
                tokenIssuer: 2 * lib.u1,
                owner: 2 * lib.u1,
                oracle: 1 * lib.u1,
                dapp: 2 * lib.u1,
                alice: 30 * 1000 * lib.u1,
                bob: 30 * 1000 * lib.u1,
                ivanov: 1 * lib.u1
            });
    });

    it('issuer issues a token', async function () {
        const tx = issue({ name: "SUPERBTC", description: "Gateway-backed BTC", quantity: 24 * 1000 * 1000 * lib.u1, decimals: 8 }, accounts.tokenIssuer)
        await broadcast(tx)
        tokenId = tx.id
        await waitForTx(tx.id)
    })

    it('issuer sends 10 BTC to ivanov', async function () {
        const tx = massTransfer(
            {
                transfers: [{ amount: 10 * lib.u1, recipient: address(accounts.ivanov) }
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
                    { type: "integer", value: 1 * lib.u1 },
                    { type: "integer", value: 100 },
                    { type: "integer", value: 5 },
                    { type: "integer", value: 5 },
                    { type: "integer", value: 5 },
                    { type: "integer", value: 0 },
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

    it('oracle sets rate', async function () { await lib.postOracle(accounts.oracle, 11200) })
  
    it('ivanov becomes lender', async function () {
       await lib.depositBtc(accounts.ivanov, tokenId)
    })
    it('when rate is 11200 and no-discount, exaclty 11200 sat for 1 waves', async function () {
        const tx = await lib.borrow(accounts.alice, 1 * lib.u1)
        const sc = await stateChanges(tx.id)
        const aliceAddress = address(accounts.alice)
        const [so, eog, eoi, eob, r, d, l] = lib.parseData(sc.data, aliceAddress)
        expect(r).to.equal(11200)
        expect(l).to.equal(11200)
        expect(d).to.equal(1 * lib.u1)
    })

    it('when waves is 1 btc, user gets 1 btc for 1 waves', async function () {
        await lib.postOracle(accounts.oracle, 1 * lib.u1)
        const tx = await lib.borrow(accounts.bob, 1 * lib.u1)
        const sc = await stateChanges(tx.id)
        const bobAddress = address(accounts.bob)
        const [so, eog, eoi, eob, r, d, l] = lib.parseData(sc.data, bobAddress)
        expect(r).to.equal(1 * lib.u1)
        expect(l).to.equal(1 * lib.u1)
        expect(d).to.equal(1 * lib.u1)
    })
})