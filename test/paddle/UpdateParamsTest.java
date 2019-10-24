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
class UpdateParamsTest {

    private Node node;
    private Account oracle, owner, dApp, alice, bob, carol, dave, eve, frank;
    private String btcId;

    private final String oracleRateKey = "waves_btc_8";
    private final int initialOracleRate = 10000;
    private final int maxRate = 50000;
    private final int discount = 90;
    private final int newDiscount = 80;

    private final int gracePeriod = 5, interestPeriod = 4, burndownPeriod = 4;
    private final int updatedGracePeriod = 1, updatedInterestPeriod = 2, updatedBurndownPeriod = 2;

    private int aliceHeight, bobHeight, carolHeight;
    private int daveHeight, eveHeight, frankHeight;

    @BeforeAll
    void before() {
        node = new Node("http://localhost:6869", 'R', "waves private node seed with waves tokens");

        async(
                () -> oracle = new Account(node, tokens(1)),
                () -> owner = new Account(node, tokens(1)),
                () -> dApp = new Account(node, tokens(4)),
                // these users will borrow for initial rate
                () -> alice = new Account(node, tokens(100)),
                () -> bob = new Account(node, tokens(100)),
                () -> carol = new Account(node, tokens(100)),
                // these users will borrow for updated rate
                () -> dave = new Account(node, tokens(100)),
                () -> eve = new Account(node, tokens(100)),
                () -> frank = new Account(node, tokens(100))
        );
        async(
                () -> oracle.writes(d -> d.integer(oracleRateKey, initialOracleRate)),
                () -> btcId = dApp.issues(a -> a.quantity(tokens(24_000)).decimals(8)).getId().toString(),
                () -> dApp.setsScript(s -> s.script(fromFile("ride/pawnshop_wbtc.ride")))
        );
        dApp.invokes(i -> i.function("init",
                arg(owner.address()), arg(btcId), arg(oracle.address()), arg(maxRate),
                arg(discount), arg(gracePeriod), arg(interestPeriod), arg(burndownPeriod)
        ));
    }

    @Test @Order(1)
    void usersCanBorrowOnInitialRates() { async(
            () -> aliceHeight = alice.invokes(i -> i
                    .dApp(dApp).function("borrow").wavesPayment(tokens(50))).getHeight(),
            () -> bobHeight = bob.invokes(i -> i
                    .dApp(dApp).function("borrow").wavesPayment(tokens(50))).getHeight(),
            () -> carolHeight = carol.invokes(i -> i
                    .dApp(dApp).function("borrow").wavesPayment(tokens(50))).getHeight()
    ); }

    @Test @Order(5)
    void ownerAndOracleCanUpdateParams() { async(
            () -> owner.invokes(i -> i.dApp(dApp).function("updateParams",
                    arg(oracle.address()), arg(maxRate), arg(newDiscount),
                    arg(updatedGracePeriod), arg(updatedInterestPeriod), arg(updatedBurndownPeriod))),
            () -> oracle.writes(d -> d.integer(oracleRateKey, initialOracleRate + 5000))
    ); }

    @Test @Order(15)
    void aliceCanBuybackInLastOfGrace() {
        node.waitForHeight(aliceHeight + gracePeriod);

        InvokeScriptTransaction tx = alice.invokes(i -> i
                .dApp(dApp).function("buyBack").payment(tokens(0.0045), btcId)
        );

        Changes changes = Changes.of(alice, node.api.stateChanges(tx.getId().toString()));

        assertAll("Check state changes",
                () -> assertThat(tx.getHeight()).isEqualTo(aliceHeight + gracePeriod),

                () -> assertThat(changes.data).hasSize(7).allMatch(d -> d.asInteger() == 0),
                () -> assertThat(changes.transfers).hasSize(1),

                () -> assertThat(changes.transferToUser().amount).isEqualTo(tokens(50)),
                () -> assertThat(changes.transferToUser().asset).isNull()
        );
    }

    @Test @Order(20)
    void bobCanBuybackAfterGrace() {
        node.waitForHeight(bobHeight + gracePeriod + 1);

        InvokeScriptTransaction tx = bob.invokes(i -> i
                .dApp(dApp).function("buyBack").payment(tokens(0.0045), btcId)
        );

        Changes changes = Changes.of(bob, node.api.stateChanges(tx.getId().toString()));

        assertAll("Check state changes",
                () -> assertThat(tx.getHeight()).isEqualTo(bobHeight + gracePeriod + 1),

                () -> assertThat(changes.data).hasSize(7).allMatch(d -> d.asInteger() == 0),
                () -> assertThat(changes.transfers).hasSize(2),

                () -> assertThat(changes.transferToUser().amount).isEqualTo(tokens(37.5)),
                () -> assertThat(changes.transferToUser().asset).isNull(),

                () -> assertThat(changes.transferToAnother().address).isEqualTo(owner.address()),
                () -> assertThat(changes.transferToAnother().amount).isEqualTo(tokens(12.5)),
                () -> assertThat(changes.transferToAnother().asset).isNull()
        );
    }

    @Test @Order(30)
    void carolCanNotBuybackAfterInterest() {
        node.waitForHeight(carolHeight + gracePeriod + interestPeriod);

        assertThat(assertThrows(NodeError.class,
                () -> carol.invokes(i -> i.dApp(dApp).function("buyBack").payment(tokens(0.0045), btcId))
        )).hasMessageContaining("your loan has expired");
    }

    @Test @Order(35)
    void usersCanBorrowOnUpdatedRates() {
        async(
                () -> daveHeight = dave.invokes(i -> i.dApp(dApp).function("borrow")
                        .wavesPayment(tokens(50))).getHeight(),
                () -> eveHeight = eve.invokes(i -> i.dApp(dApp).function("borrow")
                        .wavesPayment(tokens(50))).getHeight(),
                () -> frankHeight = frank.invokes(i -> i.dApp(dApp).function("borrow")
                        .wavesPayment(tokens(50))).getHeight()
        );

        assertAll("all users received tokens",
                () -> assertThat(dave.balance(btcId)).isEqualTo(tokens(0.006)),
                () -> assertThat(eve.balance(btcId)).isEqualTo(tokens(0.006)),
                () -> assertThat(frank.balance(btcId)).isEqualTo(tokens(0.006))
        );
    }

    @Test @Order(40)
    void daveCanBuybackAllImmediately() {
        node.waitForHeight(daveHeight + updatedGracePeriod);

        InvokeScriptTransaction tx = dave.invokes(i -> i
                .dApp(dApp).function("buyBack").payment(tokens(0.006), btcId)
        );

        Changes changes = Changes.of(dave, node.api.stateChanges(tx.getId().toString()));

        assertAll("Check state changes",
                () -> assertThat(tx.getHeight()).isLessThanOrEqualTo(daveHeight + updatedGracePeriod),

                () -> assertThat(changes.data).hasSize(7).allMatch(d -> d.asInteger() == 0),
                () -> assertThat(changes.transfers).hasSize(1),

                () -> assertThat(changes.transferToUser().amount).isEqualTo(tokens(50)),
                () -> assertThat(changes.transferToUser().asset).isNull()
        );
    }

    @Test @Order(45)
    void eveCanBuybackInLastBlockOfInterest() {
        node.waitForHeight(eveHeight + updatedGracePeriod + updatedInterestPeriod - 1);

        InvokeScriptTransaction tx = eve.invokes(i -> i
                .dApp(dApp).function("buyBack").payment(tokens(0.006), btcId)
        );

        Changes changes = Changes.of(eve, node.api.stateChanges(tx.getId().toString()));

        assertAll("Check state changes",
                () -> assertThat(tx.getHeight()).isEqualTo(eveHeight + updatedGracePeriod + updatedInterestPeriod - 1),

                () -> assertThat(changes.data).hasSize(7).allMatch(d -> d.asInteger() == 0),
                () -> assertThat(changes.transfers).hasSize(2),

                () -> assertThat(changes.transferToUser().amount).isEqualTo(tokens(25)),
                () -> assertThat(changes.transferToUser().asset).isNull(),

                () -> assertThat(changes.transferToAnother().address).isEqualTo(owner.address()),
                () -> assertThat(changes.transferToAnother().amount).isEqualTo(tokens(25)),
                () -> assertThat(changes.transferToAnother().asset).isNull()
        );
    }

    @Test @Order(50)
    void frankCanNotBuybackAfterInterest() {
        node.waitForHeight(frankHeight + updatedGracePeriod + updatedInterestPeriod);

        assertThat(assertThrows(NodeError.class,
                () -> frank.invokes(i -> i.dApp(dApp).function("buyBack").payment(tokens(0.006), btcId))
        )).hasMessageContaining("your loan has expired");
    }

    @Test @Order(55)
    void aliceCantBuybackOnInterestIfIts1() {
        owner.invokes(i -> i.dApp(dApp).function("updateParams",
                arg(oracle.address()), arg(maxRate), arg(newDiscount),
                arg(1), arg(1), arg(1)));

        aliceHeight = alice.invokes(i -> i
                .dApp(dApp).function("borrow").wavesPayment(tokens(50))).getHeight();

        node.waitForHeight(aliceHeight + gracePeriod + 1);

        assertThat(assertThrows(NodeError.class,
                () -> alice.invokes(i -> i.dApp(dApp).function("buyBack").payment(tokens(0.006), btcId))
        )).hasMessageContaining("your loan has expired");
    }

}
