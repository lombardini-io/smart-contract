package paddle;

import com.wavesplatform.wavesj.transactions.InvokeScriptTransaction;
import im.mak.paddle.Account;
import im.mak.paddle.Node;
import im.mak.paddle.exceptions.NodeError;
import org.junit.jupiter.api.*;
import paddle.util.Changes;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.actions.invoke.Arg.arg;
import static im.mak.paddle.actions.mass.Recipient.to;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import static paddle.util.Token.tokens;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PawnshopTest {

    private Node node;
    private Account oracle, issuer, owner, dApp, alice, bob, cooper;
    private String btcId;
    private int bobHeight, cooperHeight;

    private final String oracleRateKey = "waves_btc_8";
    private final int initialOracleRate = 10000;
    private final int maxRate = 50000;
    private final int discount = 80; // should lend only 80% of rate
    private final int gracePeriod = 5;
    private final int interestPeriod = 4;
    private final int burndownPeriod = 10;

    private final long alicePayment = tokens(10000);

    @BeforeAll
    void before() {
        node = new Node("http://localhost:6869", 'R', "waves private node seed with waves tokens");

        async(
                () -> issuer = new Account(node, tokens(2)),
                () -> oracle = new Account(node, tokens(1)),
                () -> owner = new Account(node, tokens(2)),
                () -> dApp = new Account(node, tokens(1)),
                () -> alice = new Account(node, tokens(30000)),
                () -> bob = new Account(node, tokens(30000)),
                () -> cooper = new Account(node, tokens(20000))
        );
        async(
                () -> oracle.writes(d -> d.integer(oracleRateKey, initialOracleRate)),
                () -> {
                    btcId = issuer.issues(a -> a.quantity(tokens(24_000_000)).decimals(8)).getId().toString();
                    issuer.massTransfers(t -> t.asset(btcId).recipients(
                            to(dApp, tokens(11)), to(alice, tokens(1))
                    ));
                },
                () -> dApp.setsScript(s -> s.script(fromFile("ride/pawnshop_wbtc.ride")))
        );
    }

    @Test
    @Order(1)
    @DisplayName("dapp sets token and oracle")
    void dappCanInitTokenAndOracle() {
        NodeError error = assertThrows(NodeError.class, () ->
                owner.invokes(i -> i
                        .dApp(dApp)
                        .function("init",
                                arg(owner.address()),
                                arg(btcId),
                                arg(oracle.address()),
                                arg(maxRate),
                                arg(discount),
                                arg(gracePeriod),
                                arg(interestPeriod),
                                arg(burndownPeriod)
                        ))
        );
        assertThat(error).hasMessageContaining("only dapp itself can init");

        dApp.invokes(i -> i.function("init",
                arg(owner.address()),
                arg(btcId),
                arg(oracle.address()),
                arg(maxRate),
                arg(discount),
                arg(gracePeriod),
                arg(interestPeriod),
                arg(burndownPeriod)
        ));

        /* TODO no @Verifier
        error = assertThrows(NodeError.class, () ->
                dApp.invokes(i -> i.function("init",
                        arg(owner.address()),
                        arg(btcId),
                        arg(oracle.address()),
                        arg(maxRate),
                        arg(discount),
                        arg(gracePeriod),
                        arg(interestPeriod),
                        arg(burndownPeriod)
                ))
        );
        assertThat(error).hasMessageContaining("Transaction is not allowed by account-script");*/
    }

    @Test
    @Order(5)
    @DisplayName("Owner can withdraw WBTC")
    void ownerCanWithdrawBtc() {
        owner.invokes(i -> i
                .dApp(dApp)
                .function("withdraw", arg(tokens(1)))
        );
        //TODO no one else
    }

    @Test
    @Order(10)
    @DisplayName("Alice borrows 0.8 btc for 10k waves")
    void aliceBorrows08btcFor10kWaves() {
        InvokeScriptTransaction invokeTx = alice.invokes(i -> i
                .dApp(dApp)
                .function("borrow")
                .wavesPayment(alicePayment)
        );
        Changes changes = Changes.of(alice, node.api.stateChanges(
                invokeTx.getId().toString()
        ));

        assertAll(
                () -> assertThat(changes.data).hasSize(7),
                () -> assertThat(changes.transfers).hasSize(1),

                () -> assertThat(changes.lend()).isEqualTo(
                        (long)(((double)alicePayment / initialOracleRate) * discount / 100)),
                () -> assertThat(changes.start()).isEqualTo(invokeTx.getHeight()),
                () -> assertThat(changes.graceEnd()).isEqualTo(changes.start() + gracePeriod),
                () -> assertThat(changes.interestEnd()).isEqualTo(changes.graceEnd() + interestPeriod),
                () -> assertThat(changes.burndownEnd()).isEqualTo(changes.graceEnd() + burndownPeriod),
                () -> assertThat(changes.rate()).isEqualTo((long)((double)initialOracleRate * discount / 100)),
                () -> assertThat(changes.deposit()).isEqualTo(alicePayment),

                () -> assertThat(changes.transferToUser().asset).isEqualTo(btcId),
                () -> assertThat(changes.transferToUser().amount).isEqualTo(changes.lend())
        );

        NodeError error = assertThrows(NodeError.class, () ->
                alice.invokes(i -> i
                        .dApp(dApp)
                        .function("borrow")
                        .wavesPayment(alicePayment)
                )
        );
        assertThat(error).hasMessageContaining(alice.address() + " already has an open loan");
    }

    @Test
    @Order(15)
    @DisplayName("Bob borrows 0.5 btc for 5k waves")
    void bobBorrows05btcFor5kWaves() {
        bobHeight = bob.invokes(i -> i
                .dApp(dApp)
                .function("borrow")
                .wavesPayment(tokens(5000))
        ).getHeight();
    }

    @Test
    @Order(20)
    @DisplayName("Cooper borrows 0.3 btc for 3k waves")
    void cooperBorrows03btcFor3kWaves() {
        cooperHeight = cooper.invokes(i -> i
                .dApp(dApp)
                .function("borrow")
                .wavesPayment(tokens(3000))
        ).getHeight();
    }

    @Test
    @Order(23)
    @DisplayName("Alice cant buy back with more, less or different token")
    void aliceCantBuybackWithDifferentToken() {
        assertAll(
                () -> assertThat(assertThrows(NodeError.class, () ->
                        alice.invokes(i -> i.dApp(dApp).function("buyBack")
                                .wavesPayment(tokens(0.8))
                        ))).hasMessageContaining("Error"),

                () -> assertThat(assertThrows(NodeError.class, () ->
                        alice.invokes(i -> i.dApp(dApp).function("buyBack")
                                .payment(tokens(0.8) - 1, btcId)
                        ))).hasMessageContaining("User must return"),

                () -> assertThat(assertThrows(NodeError.class, () ->
                        alice.invokes(i -> i.dApp(dApp).function("buyBack")
                                .payment(tokens(0.8) + 1, btcId)
                        ))).hasMessageContaining("User must return")
        );
    }

    @Test
    @Order(25)
    @DisplayName("Alice buys back 10k waves for 0.8 btc that has been borrowed")
    void aliceCanBuybackAll() {
        InvokeScriptTransaction invokeTx = alice.invokes(i -> i
                .dApp(dApp)
                .function("buyBack")
                .payment(tokens(0.8), btcId)
        );
        Changes changes = Changes.of(alice, node.api.stateChanges(
                invokeTx.getId().toString()
        ));

        assertAll(
                () -> assertThat(changes.data).hasSize(7),
                () -> assertThat(changes.transfers).hasSize(1),

                () -> assertThat(changes.lend()).isEqualTo(0),
                () -> assertThat(changes.start()).isEqualTo(0),
                () -> assertThat(changes.graceEnd()).isEqualTo(0),
                () -> assertThat(changes.interestEnd()).isEqualTo(0),
                () -> assertThat(changes.burndownEnd()).isEqualTo(0),
                () -> assertThat(changes.rate()).isEqualTo(0),
                () -> assertThat(changes.deposit()).isEqualTo(0),

                () -> assertThat(changes.transferToUser().asset).isEqualTo(null),
                () -> assertThat(changes.transferToUser().amount).isEqualTo(alicePayment)
        );
    }

    @Test
    @Order(26)
    @DisplayName("Alice can't buyback again")
    void aliceCantBuybackAgain() {
        assertThat(assertThrows(NodeError.class, () ->
                alice.invokes(i -> i.dApp(dApp).function("buyBack")
                        .payment(tokens(0.8), btcId)
                )
        )).hasMessageContaining("No open loan");

        String txId = alice.transfers(t -> t
                .to(dApp).amount(tokens(0.8)).asset(btcId)
        ).getId().toString();

        assertThat(assertThrows(NodeError.class, () ->
                alice.invokes(i -> i.dApp(dApp)
                        .function("restoreBuyBack", arg(txId))
                )
        )).hasMessageContaining("No open loan");
    }

    @Test
    @Order(27)
    @DisplayName("Fails to borrow if rate is suspicious")
    void failBorrowIfRateIsSuspicious() {
        oracle.writes(d -> d.integer(oracleRateKey, 50001));

        assertThat(assertThrows(NodeError.class, () ->
                alice.invokes(i -> i.dApp(dApp).function("borrow")
                        .wavesPayment(tokens(2000))
                )
        )).hasMessageContaining("Suspicious");
    }

    @Test
    @Order(30)
    @DisplayName("Alice can perform the operation again")
    void aliceCanPerformTheOperationAgain() {
        oracle.writes(d -> d.integer(oracleRateKey, 20000));

        alice.invokes(i -> i
                .dApp(dApp)
                .function("borrow")
                .wavesPayment(tokens(2000))
        );
        alice.invokes(i -> i
                .dApp(dApp)
                .function("buyBack")
                .payment(2000L * 20000 * discount / 100, btcId)
        );
    }

    @Test
    @Order(33)
    @DisplayName("Alice can buyback with transaction")
    void aliceCanBuybackWithTransaction() {
        alice.invokes(i -> i.dApp(dApp).function("borrow").wavesPayment(tokens(2000)));

        String txId = alice.transfers(t -> t
                .to(dApp).amount(2000L * 20000 * discount / 100).asset(btcId)
        ).getId().toString();

        Changes changes = Changes.of(alice, node.api.stateChanges(
                alice.invokes(i -> i
                        .dApp(dApp)
                        .function("restoreBuyBack", arg(txId))
                ).getId().toString()
        ));

        assertAll(
                () -> assertThat(changes.data).hasSize(8),
                () -> assertThat(changes.transfers).hasSize(1),

                () -> assertThat(changes.lend()).isEqualTo(0),
                () -> assertThat(changes.start()).isEqualTo(0),
                () -> assertThat(changes.graceEnd()).isEqualTo(0),
                () -> assertThat(changes.interestEnd()).isEqualTo(0),
                () -> assertThat(changes.burndownEnd()).isEqualTo(0),
                () -> assertThat(changes.rate()).isEqualTo(0),
                () -> assertThat(changes.deposit()).isEqualTo(0),
                () -> assertThat(changes.registeredTx(txId)).isTrue(),

                () -> assertThat(changes.transferToUser().asset).isEqualTo(null),
                () -> assertThat(changes.transferToUser().amount).isEqualTo(tokens(2000))
        );
    }

    @Test
    @Order(35)
    @DisplayName("Bob buys back after grace period ends, loses some waves of old rate")
    void bobCanBuybackAfterGracePeriod() {
        node.waitForHeight(bobHeight + gracePeriod + 2);
        Changes changes = Changes.of(bob, node.api.stateChanges(bob.invokes(i -> i
                .dApp(dApp)
                .function("buyBack")
                .payment(tokens(0.4), btcId)
        ).getId().toString()));
        assertThat(changes.transferToUser().amount).isEqualTo(tokens(4000)); // 2 of 10 burndown, loses 20% of deposit
    }

    @Test
    @Order(40)
    @DisplayName("Cooper doesnt buy back during interest period, owner makes closeExpiredFor")
    void ownerCanCloseExpired() {
        node.waitForHeight(cooperHeight + gracePeriod + interestPeriod + 2);
        owner.invokes(i -> i
                .dApp(dApp)
                .function("closeExpiredFor",
                        arg(cooper.address())
                )
        );
    }

    //TODO assert all throw
    //TODO restoreBuyBack - если по ошибке трансфер. А сделать restore для borrow?
    //TODO что если нет бабла/битков на dapp?
    //TODO check rates correctness on init and update

}
