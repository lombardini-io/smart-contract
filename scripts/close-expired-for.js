const dappAddress = "3MwHAghvVSNQUvsYHsHSR4tAEYiVarQRrtG"

async function go() {
    const tx = invokeScript({
        dApp: dappAddress,
        call: {
            function: "closeExpiredFor", args: [{ type: "string", value: env.for }]
        }
    },
    env.SEED
    )
    await broadcast(tx)
    await waitForTx(tx.id)
    console.log(tx.id)
}

go()