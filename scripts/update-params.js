const path = require('path')
const lib = require(path.resolve('./test/surfboard/lib.js'))

const dappAddress = "3P4ub5GDTxMMr9VAoWzvMKofXWLbbpBxqZS"
const oracleAddress = "3PPTrTo3AzR56N7ArzbU3Bpq9zYMgcf39Mk"
async function go() {
    const tx = invokeScript({
        dApp: dappAddress,
        call: {
            function: "updateParams",
            args: [
                { type: "string", value: oracleAddress },   // oracle
                { type: "integer", value: 20000 },          // maxRate
                { type: "integer", value: 80 },             // discountPercentile, / 100
                { type: "integer", value: 1440 },            // gracePeriod, blocks
                { type: "integer", value: 43200 },            // interestPeriod, blocks
                { type: "integer", value: 1440000 },        // burndownPeriod, blocks
                { type: "integer", value: 10 },             // serviceFeePercentile, / 100
                { type: "integer", value: 10 }              // lendSize, WBTC
            ]
        },
        payment: [],
        additionalFee: 400000
    },
        env.adminSeed
    )
    await broadcast(tx)
    await waitForTx(tx.id)
    console.log(tx.id)
}

go()
