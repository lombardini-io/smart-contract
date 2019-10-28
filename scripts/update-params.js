const path = require('path')
const lib = require(path.resolve('./test/surfboard/lib.js'))

const dappAddress = "3MwHAghvVSNQUvsYHsHSR4tAEYiVarQRrtG"
const oracleAddress = "3N6RM5c9TGqC8yqmLMgL1GCMtecMUX9Hxw5"
async function go() {
    const tx = invokeScript({
        dApp: dappAddress,
        call: {
            function: "updateParams",
            args: [
                { type: "string", value: oracleAddress },   // oracle
                { type: "integer", value: 20000 },          // maxRate
                { type: "integer", value: 80 },             // discountPercentile, / 100
                { type: "integer", value: 120 },            // gracePeriod, blocks
                { type: "integer", value: 240 },            // interestPeriod, blocks
                { type: "integer", value: 60*24*7 },        // burndownPeriod, blocks
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
