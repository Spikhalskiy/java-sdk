package io.temporal.internal.testing;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalActivityLongTest {
  private static final Logger log = LoggerFactory.getLogger(LocalActivityLongTest.class);

  private static final String TASK_QUEUE = "test-workflow";

  private TestWorkflowEnvironment testEnvironment;

  @Before
  public void setUp() {
    TestEnvironmentOptions options = TestEnvironmentOptions.newBuilder().build();
    testEnvironment = TestWorkflowEnvironment.newInstance(options);
  }

  @After
  public void tearDown() {
    testEnvironment.close();
  }

  @WorkflowInterface
  public interface TestWorkflow {
    @WorkflowMethod
    String workflow(String input);
  }

  public static class ActivityWorkflow implements TestWorkflow {

    private final TestActivity activity =
        Workflow.newLocalActivityStub(
            TestActivity.class, LocalActivityOptions.newBuilder().build());

    @Override
    public String workflow(String input) {
      return activity.activity(input + "3");
    }
  }

  @ActivityInterface
  public interface TestActivity {
    String activity(String input);
  }

  private static class ActivityImpl implements TestActivity {
    @Override
    public String activity(String input) {
      return "1";
    }
  }

  @Test
  public void trivialTest() {
    Worker worker = testEnvironment.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ActivityWorkflow.class);
    worker.registerActivitiesImplementations(new ActivityImpl());

    testEnvironment.start();
    WorkflowClient client = testEnvironment.getWorkflowClient();
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            // uncomment this to fix the test
            // .setWorkflowExecutionTimeout(Duration.ofHours(1))
            .setTaskQueue(TASK_QUEUE)
            .build();

    for (int reqCount = 1; reqCount < 1000; reqCount++) {
      log.info("Request {}", reqCount);
      TestWorkflow workflow = client.newWorkflowStub(TestWorkflow.class, options);
      String result = workflow.workflow(UUID.randomUUID().toString());
    }
  }
}
