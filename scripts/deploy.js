const path = require('path')
const lib = require(path.resolve('./test/surfboard/lib.js'))
async function go() {
    const tx = setScript(
        { script: compile(file("pawnshop_wbtc.ride")), additionalFee : 400000}, env.dappSeed)
    await broadcast(tx)
    await waitForTx(tx.id)
    console.log(tx.id)
}

go()