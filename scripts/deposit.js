const dappAddress = "3P4ub5GDTxMMr9VAoWzvMKofXWLbbpBxqZS"
const oracleAddress = "3PPTrTo3AzR56N7ArzbU3Bpq9zYMgcf39Mk"
async function go() {
    const tx = invokeScript({
        dApp: dappAddress,
        call: {
            function: "depositBtc",
            args: []
        },
        payment: [{amount: 10^9, assetId: "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS"}],
    },
        env.lenderSeed
    )
    await broadcast(tx)
    await waitForTx(tx.id)
    console.log(tx.id)
}

go()
