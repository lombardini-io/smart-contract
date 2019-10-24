package paddle;

import com.wavesplatform.wavesj.transactions.InvokeScriptTransaction;
import im.mak.paddle.Account;
import im.mak.paddle.Node;
import im.mak.paddle.exceptions.NodeError;
import org.junit.jupiter.api.*;
import paddle.util.Changes;

import static im.mak.paddle.Async.async;
import static im.mak.paddle.actions.invoke.Arg.arg;
import static im.mak.paddle.util.Script.fromFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import static paddle.util.Token.tokens;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorrowSatoshiTest {

    private Node node;
    private Account oracle, owner, dApp, alice, bob, carol;
    private String btcId;

    private final String oracleRateKey = "waves_btc_8";
    private final int initialOracleRate = 15000;
    private final int maxRate = 50000;
    private final int discount = 80; // should lend only 80% of rate
    private final int gracePeriod = 5;
    private final int interestPeriod = 4;
    private final int burndownPeriod = 4;

    private int aliceHeight, bobHeight, carolHeight;

    @BeforeAll
    void before() {
        node = new Node("http://localhost:6869", 'R', "waves private node seed with waves tokens");

        async(
                () -> oracle = new Account(node, tokens(1)),
                () -> owner = new Account(node, tokens(1)),
                () -> dApp = new Account(node, tokens(5)),
                () -> alice = new Account(node, tokens(1)),
                () -> bob = new Account(node, tokens(1)),
                () -> carol = new Account(node, tokens(1))
        );
        async(
                () -> oracle.writes(d -> d.integer(oracleRateKey, initialOracleRate)),
                () -> btcId = dApp.issues(a -> a.quantity(tokens(100)).decimals(8)).getId().toString(),
                () -> dApp.setsScript(s -> s.script(fromFile("ride/pawnshop_wbtc.ride")))
        );
        dApp.invokes(i -> i.function("init", arg(owner.address()), arg(btcId), arg(oracle.address()),
                arg(maxRate), arg(discount), arg(gracePeriod), arg(interestPeriod), arg(burndownPeriod)
        ));
    }

    @Test
    @Order(1)
    void usersCantBorrowOneSatoshiLessThanRate() {
        assertThat(assertThrows(NodeError.class, () ->
                alice.invokes(i -> i.dApp(dApp).function("borrow").wavesPayment(8333))
        )).hasMessageContaining("payment can't be less than 8334 wavelets (price of 1 satoshi)");
    }

    @Test
    @Order(10)
    void usersCanBorrowOneSatoshi() {
        async(
                () -> aliceHeight = alice.invokes(i -> i
                        .dApp(dApp).function("borrow").wavesPayment(8334)).getHeight(),
                () -> bobHeight = bob.invokes(i -> i
                        .dApp(dApp).function("borrow").wavesPayment(8334)).getHeight(),
                () -> carolHeight = carol.invokes(i -> i
                        .dApp(dApp).function("borrow").wavesPayment(8334)).getHeight()
        );
    }

    @Test
    @Order(20)
    void aliceCanBuybackInLastGrace() {
        node.waitForHeight(aliceHeight + gracePeriod);

        InvokeScriptTransaction tx = alice.invokes(i -> i
                .dApp(dApp)
                .function("buyBack")
                .payment(tokens(0.00000001), btcId)
        );

        Changes changes = Changes.of(alice, node.api.stateChanges(tx.getId().toString()));

        assertAll("Check state changes",
                () -> assertThat(tx.getHeight()).isEqualTo(aliceHeight + gracePeriod),

                () -> assertThat(changes.data).hasSize(7).allMatch(v -> v.asInteger() == 0),
                () -> assertThat(changes.transfers).hasSize(1),

                () -> assertThat(changes.transferToUser().amount).isEqualTo(8334),
                () -> assertThat(changes.transferToUser().asset).isNull()
        );
    }

    @Test
    @Order(30)
    void bobCanBuybackInLastBlockOfInterest() {
        node.waitForHeight(bobHeight + gracePeriod + interestPeriod - 1);

        InvokeScriptTransaction tx = bob.invokes(i -> i
                .dApp(dApp)
                .function("buyBack")
                .payment(tokens(0.00000001), btcId)
        );

        Changes changes = Changes.of(bob, node.api.stateChanges(tx.getId().toString()));

        System.out.println();
        assertAll("Check state changes",
                () -> assertThat(tx.getHeight()).isEqualTo(bobHeight + gracePeriod + interestPeriod - 1),

                () -> assertThat(changes.data).hasSize(7).allMatch(d -> d.asInteger() == 0),
                () -> assertThat(changes.transfers).hasSize(2),

                () -> assertThat(changes.transferToUser().amount).isEqualTo(2083),
                () -> assertThat(changes.transferToUser().asset).isNull(),

                () -> assertThat(changes.transferToAnother().address).isEqualTo(owner.address()),
                () -> assertThat(changes.transferToAnother().amount).isEqualTo(6251),
                () -> assertThat(changes.transferToAnother().asset).isNull()
        );
    }

    @Test
    @Order(30)
    void carolCantBuybackAfterInterest() {
        node.waitForHeight(carolHeight + gracePeriod + interestPeriod);

        assertThat(assertThrows(NodeError.class, () ->
                carol.invokes(i -> i
                        .dApp(dApp)
                        .function("buyBack")
                        .payment(tokens(0.00000001), btcId))
        )).hasMessageContaining("your loan has expired");
    }

}
