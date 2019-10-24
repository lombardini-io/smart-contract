const lib = {
    u1: 100*1000*1000,
    oracleKey : "waves_btc_8",



    parseData: (data, addr) => {
        const so = data.find(k => k.key == "start_of_" + addr).value
        const eog = data.find(k => k.key == "end_of_grace_of_" + addr).value
        const eoi = data.find(k => k.key == "end_of_interest_of_" + addr).value
        const eob = data.find(k => k.key == "end_of_burndown_of_" + addr).value
        const r = data.find(k => k.key == "rate_of_" + addr).value
        const d = data.find(k => k.key == "deposit_of_" + addr).value
        const l = data.find(k => k.key == "lend_of_" + addr).value
        const ls = data.find(k => k.key == "lenders_of_" + addr).value
        return [so, eog, eoi, eob, r, d, l, ls]
    },


    parsePayouts: (data, addr) => {
        const x = data.find(k => k.key == "profit_for_" + addr)
        const profit = (x==null) ? null : x.value
        const y = data.find(k => k.key == "unclaimied_for_" + addr)
        const unclaimed = (y==null) ? null : y.value
        return [profit, unclaimed]
    },

    postOracle: async (oracleAcc, oracleValue) => {
        const tx = data({
            data: [
                { key: lib.oracleKey, type: "integer", value: oracleValue },
            ]
        },
        oracleAcc
        )
        await broadcast(tx)
        await waitForTx(tx.id)
    },

    buildBorrowTx: (account, amount) => {
        return invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "borrow", args: []
            },
            payment: [{ amount: amount, assetId: null }]
        }, account);
    },

    buildTakeProfitTx: (account) => {
        return invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "takeProfit", args: []
            },
            payment: []
        }, account);
    },

    
    buildDepositBtcTx: (account, assetId, amount = 10 * lib.u1) => {
        return invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "depositBtc", args: []
            },
            payment: [{ amount: amount, assetId: assetId }]
        }, account);
    },

    buildEnableLending: (account, flag) => {
        return invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "enableLending", args: [{type: "boolean", value: flag}]
            },
            payment: []
        }, account);
    },

    buildWithdrawBtcTx: (account) => {
        return invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "withdrawBtc", args: []
            },
            payment: []
        }, account);
    },

    borrow: async (account, amount) => {
        const tx = lib.buildBorrowTx(account, amount)
        await broadcast(tx);
        await waitForTx(tx.id);
        return tx;
    },

    takeProfit: async (account) => {
        const tx = lib.buildTakeProfitTx(account)
        await broadcast(tx);
        await waitForTx(tx.id);
        return tx;
    },

    depositBtc: async (account, assetId) => {
        const tx = lib.buildDepositBtcTx(account, assetId)
        await broadcast(tx);
        await waitForTx(tx.id);
        return tx;
    },

    enableLending: async (account,flag) => {
        const tx = lib.buildEnableLending(account, flag)
        await broadcast(tx);
        await waitForTx(tx.id);
        return tx;
    },

    withdrawBtc: async (account) => {
        const tx = lib.buildWithdrawBtcTx(account)
        await broadcast(tx);
        await waitForTx(tx.id);
        return tx;
    },

    buildBuyBackTx: (account, amount, assetId) => {
        return invokeScript({
            dApp: address(accounts.dapp),
            call: {
                function: "buyBack", args: []
            },
            payment: [{ amount: amount, assetId: assetId }]
        },
            account
        )
    }
}

module.exports = lib;