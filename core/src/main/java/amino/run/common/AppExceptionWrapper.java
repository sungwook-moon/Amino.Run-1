package amino.run.common;

/** Created by Venugopal Reddy K 00900280 on 11/8/18. */
/**
 * MicroService exceptions are wrapped into this runtime exception to isolate them from Amino.Run
 * framework exceptions
 */
public class AppExceptionWrapper extends RuntimeException {
    private Exception e;

    public AppExceptionWrapper(Exception e) {
        this.e = e;
    }

    public Exception getException() {
        return e;
    }
}
