const lib = require('./lib.js')

describe('lenders test suite', async function () {
    const query = (key) => accountDataByKey(key, address(accounts.dapp))
    const queryLenders = () => query("lenders")
    const queryEnabledLenders = () => query("enabledLenders")
    const queryOpenLends = (acc) => query("open_lends_of_" + acc)
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
                rob: 1 * lib.u1,
                ivanov: 1 * lib.u1,
                masha: 1 * lib.u1
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
                transfers: [
                    { amount: 10 * lib.u1, recipient: address(accounts.ivanov) },
                    { amount: 10 * lib.u1, recipient: address(accounts.masha) },
                    { amount: 10 * lib.u1, recipient: address(accounts.rob) }
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
  
    it('cant lend anything except for 10 btc', async function () { 
        await expect(broadcast(lib.buildDepositBtcTx(accounts.ivanov, tokenId, 42))).to.be.rejectedWith("must be attached")
        await expect(broadcast(lib.buildDepositBtcTx(accounts.ivanov, null, 10))).to.be.rejectedWith("must be attached")
    })

    it('ivanov becomes lender', async function () { 
        await lib.depositBtc(accounts.ivanov, tokenId)
        expect((await queryLenders()).value).to.equal(address(accounts.ivanov))
        expect((await queryEnabledLenders()).value).to.equal(address(accounts.ivanov))
    })

    it('bob takes loan', async function () {
        await lib.postOracle(accounts.oracle, 1 * lib.u1)
        const tx = await lib.borrow(accounts.bob, 1 * lib.u1)
        const sc = await stateChanges(tx.id)
        const bobAddress = address(accounts.bob)
        const [so, eog, eoi, eob, r, d, l, ls] = lib.parseData(sc.data, bobAddress)
        expect(ls).to.equal(address(accounts.ivanov))
        expect((await queryOpenLends(address(accounts.ivanov))).value).to.equal(1)
    })

    it('masha becomes lender', async function () {
         await lib.depositBtc(accounts.masha, tokenId)
         const sm = address(accounts.ivanov) +  "|" + address(accounts.masha)
         expect((await queryLenders()).value).to.equal(sm)
         expect((await queryEnabledLenders()).value).to.equal(sm)
        })
    
    it('rob becomes lender', async function () { 
        await lib.depositBtc(accounts.rob, tokenId) 
        const smr = address(accounts.ivanov) +  "|" + address(accounts.masha) + "|" + address(accounts.rob)
        expect((await queryLenders()).value).to.equal(smr)
        expect((await queryEnabledLenders()).value).to.equal(smr)
    })
    it('ivanov cant quit until the loan is closed', async function () {
        await expect(broadcast(lib.buildWithdrawBtcTx(accounts.ivanov))).to.be.rejectedWith("withdraw not allowed")
    })

    it('bob returns his loan', async function () {
        const tx = lib.buildBuyBackTx(accounts.bob, 1 * lib.u1, tokenId)
        await broadcast(tx)
        await waitForTx(tx.id)

        const bobAddress = address(accounts.bob)
        const sc = await stateChanges(tx.id)

        const transfer = sc.transfers[0]
        expect(transfer.address).to.equal(address(accounts.bob))
        expect(transfer.asset).to.equal(null)
        expect(transfer.amount).to.equal(lib.u1)

        
        const [so, eog, eoi, eob, r, d, l,ls] = lib.parseData(sc.data, bobAddress)
        expect(ls).to.equal("")
        expect(transfer.address).to.equal(address(accounts.bob))
        expect(transfer.asset).to.equal(null)
        expect(transfer.amount).to.equal(1 * lib.u1)

        const [profit, unclaimed] = lib.parsePayouts(sc.data, address(accounts.ivanov))
        expect(profit).to.equal(0)
        expect(unclaimed).to.equal(null)
        expect((await queryOpenLends(address(accounts.ivanov))).value).to.equal(0)

    })

   it('rob freezes his account for future loans', async function () { 
       await lib.enableLending(accounts.rob, false)
       const smr = address(accounts.ivanov) +  "|" + address(accounts.masha) + "|" + address(accounts.rob)
       expect((await queryLenders()).value).to.equal(smr)
       expect((await queryEnabledLenders()).value).to.equal(address(accounts.ivanov) +  "|" + address(accounts.masha)) 
    }) 

   it('alice takes loan', async function () {
    await lib.postOracle(accounts.oracle, 1 * lib.u1)
    const tx = await lib.borrow(accounts.alice, 1 * lib.u1)
    const sc = await stateChanges(tx.id)
    const bobAddress = address(accounts.alice)
    const [so, eog, eoi, eob, r, d, l,ls] = lib.parseData(sc.data, address(accounts.alice))
    expect(ls).to.equal(address(accounts.ivanov) + "|" + address(accounts.masha))
    expect((await queryOpenLends(address(accounts.ivanov))).value).to.equal(1)
    expect((await queryOpenLends(address(accounts.masha))).value).to.equal(1)
    expect((await queryOpenLends(address(accounts.rob)))).to.equal(null)
})

   it('bob can quit', async function () {
    const tx = await lib.withdrawBtc(accounts.rob)
    const sm = address(accounts.ivanov) +  "|" + address(accounts.masha)
    expect((await queryLenders()).value).to.equal(sm)
    expect((await queryEnabledLenders()).value).to.equal(sm)
   })
})
