package amino.run.policy.scalability;

import static amino.run.common.UtilsTest.extractFieldValueOnInstance;
import static amino.run.common.UtilsTest.setFieldValueOnInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import amino.run.app.DMSpec;
import amino.run.app.Language;
import amino.run.app.MicroServiceSpec;
import amino.run.common.BaseTest;
import amino.run.sampleSO.SO;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;

/** Created by Vishwajeet on 2/4/18. */
@RunWith(PowerMockRunner.class)
public class LoadBalancedFrontendPolicyTest extends BaseTest {
    int exceptionExpected = 0;

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {

        LoadBalancedFrontendPolicy.Config config = new LoadBalancedFrontendPolicy.Config();
        config.setMaxConcurrentReq(2);
        config.setReplicaCount(2);

        MicroServiceSpec spec =
                MicroServiceSpec.newBuilder()
                        .setLang(Language.java)
                        .setJavaClassName("amino.run.sampleSO.SO")
                        .addDMSpec(
                                DMSpec.newBuilder()
                                        .setName(LoadBalancedFrontendPolicy.class.getName())
                                        .addConfig(config)
                                        .create())
                        .create();
        super.setUp(2, spec);
    }

    /**
     * Client side DM instance should randomise the order in which it performs round robin against
     * replicas. Here we have instantiated two servers. Anytime the client makes an onRPC call, the
     * first request is randomly assigned to any server. Subsequent onRPC call is always redirected
     * to the other server and so on.
     */
    @Test
    public void testRandomLoadBalance() throws Exception {
        String setMethodName = "public void amino.run.sampleSO.SO.setI(java.lang.Integer)";
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(new Integer(2));
        this.client.onRPC(setMethodName, params);
        this.client.onRPC(setMethodName, params);
        assertEquals(((SO) server1.getAppObject().getObject()).getI().intValue(), 2);
        assertEquals(((SO) server2.getAppObject().getObject()).getI().intValue(), 2);
    }

    /**
     * If the number of concurrent requests against a given replica exceeds the
     * MAX_CONCURRENT_REQUESTS, requests to that server replica should fail with a
     * ServerOverLoadException.
     */
    @Test
    public void testMaxConcurrentRequests() throws Exception {
        final String methodName = "public java.lang.Integer amino.run.sampleSO.SO.getIDelayed()";
        final ArrayList<Object> params = new ArrayList<Object>();
        Integer max = (Integer) extractFieldValueOnInstance(this.server1, "maxConcurrentReq");

        List<FutureTask<Object>> taskList = new ArrayList<FutureTask<Object>>();
        for (int i = 0; i < 5 * max; i++) {
            FutureTask<Object> task =
                    new FutureTask<Object>(
                            new Callable<Object>() {
                                @Override
                                public Object call() throws Exception {
                                    Object test = null;
                                    try {
                                        test = client.onRPC(methodName, params);
                                    } catch (ServerOverLoadException e) {
                                        exceptionExpected++;
                                    }
                                    return test;
                                }
                            });
            taskList.add(task);
        }

        // Run tasks in parallel
        ExecutorService executor = Executors.newFixedThreadPool(taskList.size());
        for (FutureTask<Object> t : taskList) {
            executor.execute(t);
        }

        for (int i = 0; i < taskList.size(); i++) {
            Object ret = taskList.get(i).get();
        }
        assertNotEquals("Passed", 0, exceptionExpected);
    }

    /**
     * If the created number of replicas is lesser than the configured number of replicas, it throws
     * an error.
     */
    @Test
    public void testStaticReplicaCount() throws Exception {
        LoadBalancedFrontendPolicy.GroupPolicy group1;
        group1 = Mockito.spy(LoadBalancedFrontendPolicy.GroupPolicy.class);

        // Expecting error message- Configured replicas count: 5, created replica count : 2
        thrown.expectMessage("Configured replicas count: 5, created replica count : 2");
        setFieldValueOnInstance(group1, "replicaCount", 5);
        group1.setSpec(new MicroServiceSpec());
        group1.onCreate("", client.getServer());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
