package io.temporal.internal.replay;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncorrectExceptionHandling {
  private static final Logger log = LoggerFactory.getLogger(IncorrectExceptionHandling.class);

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

    private final TestActivity1 activity1 =
        Workflow.newLocalActivityStub(
            TestActivity1.class,
            LocalActivityOptions.newBuilder()
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(2))
                        .build())
                .setStartToCloseTimeout(Duration.ofMinutes(2))
                .build());

    @Override
    public String workflow(String input) {
      return activity1.activity1(input);
    }
  }

  @ActivityInterface
  public interface TestActivity1 {
    String activity1(String input);
  }

  private static class Activity1Impl implements TestActivity1 {
    @Override
    public String activity1(String input) {
      return Workflow.randomUUID().toString();
    }
  }

  @Test
  public void trivialTest() {
    Worker worker = testEnvironment.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ActivityWorkflow.class);
    worker.registerActivitiesImplementations(new Activity1Impl());

    testEnvironment.start();
    WorkflowClient client = testEnvironment.getWorkflowClient();
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            .setTaskQueue(TASK_QUEUE)
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setMaximumAttempts(1)
                    .setInitialInterval(Duration.ofMinutes(2))
                    .build())
            .build();

    TestWorkflow workflow = client.newWorkflowStub(TestWorkflow.class, options);
    workflow.workflow(UUID.randomUUID().toString());
  }
}
