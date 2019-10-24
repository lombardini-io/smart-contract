package paddle.util;

import java.math.BigDecimal;

public class Token {

    public static long tokens(double tokens) {
        return BigDecimal.valueOf(tokens)
                .multiply(BigDecimal.valueOf(100000000))
                .longValueExact();
    }

}
