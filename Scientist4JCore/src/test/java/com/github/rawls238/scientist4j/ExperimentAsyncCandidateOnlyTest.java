package com.github.rawls238.scientist4j;

import com.github.rawls238.scientist4j.exceptions.MismatchException;
import com.github.rawls238.scientist4j.metrics.NoopMetricsProvider;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ExperimentAsyncCandidateOnlyTest {

    private Integer exceptionThrowingFunction() {
      throw new RuntimeException("throw an exception");
    }

  private Integer sleepFunction() {
    try {
      Thread.sleep(1001);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return 3;
  }

  private Integer shortSleepFunction() {
    try {
      Thread.sleep(101);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return 3;
  }

    private Integer safeFunction() {
      return 3;
    }

    private Integer safeFunctionWithDifferentResult() {
      return 4;
    }

    @Test
    public void itThrowsAnExceptionWhenControlFails() {
      Experiment<Integer> experiment = new Experiment<>("test", new NoopMetricsProvider());
      boolean controlThrew = false;
      try {
        experiment.runAsyncCandidateOnly(this::exceptionThrowingFunction, this::exceptionThrowingFunction);
      } catch (RuntimeException e) {
        controlThrew = true;
      } catch (Exception e) {

      }
      assertThat(controlThrew).isEqualTo(true);
    }

  @Test
  public void itDoesntThrowAnExceptionWhenCandidateFails() {
    Experiment<Integer> experiment = new Experiment<>("test", new NoopMetricsProvider());
    boolean candidateThrew = false;
    Integer val = 0;
    try {
      val = experiment.runAsyncCandidateOnly(this::safeFunction, this::exceptionThrowingFunction);
    } catch (Exception e) {
      candidateThrew = true;
    }
    assertThat(candidateThrew).isEqualTo(false);
    assertThat(val).isEqualTo(3);
  }

  @Test
  public void itThrowsOnMismatch() {
    Experiment<Integer> experiment = new Experiment<>("test", true, new NoopMetricsProvider());
    boolean candidateThrew = false;
    try {
      experiment.runAsyncCandidateOnly(this::safeFunction, this::safeFunctionWithDifferentResult);
    } catch (MismatchException e) {
      candidateThrew = true;
    } catch (Exception e) {

    }

    assertThat(candidateThrew).isEqualTo(true);
  }

  @Test
  public void itDoesNotThrowOnMatch() {
    Experiment<Integer> exp = new Experiment<>("test", true, new NoopMetricsProvider());
    boolean candidateThrew = false;
    Integer val = 0;
    try {
      val = exp.runAsync(this::safeFunction, this::safeFunction);
    } catch (Exception e) {
      candidateThrew = true;
    }

    assertThat(val).isEqualTo(3);
    assertThat(candidateThrew).isEqualTo(false);
  }

  @Test
  public void itWorksWithAnExtendedClass() {
    Experiment<Integer> exp = new TestPublishExperiment<>("test", new NoopMetricsProvider());
    try {
      exp.run(this::safeFunction, this::safeFunction);
    } catch (Exception e) {

    }
  }

  @Test
  public void asyncRunsFaster() {
    Experiment<Integer> exp = new Experiment<>("test", false, new NoopMetricsProvider());
    boolean candidateThrew = false;
    Integer val = 0;
    Date date1 = new Date();

    try {
      val = exp.runAsyncCandidateOnly(this::sleepFunction, this::sleepFunction);
    } catch (Exception e) {
      candidateThrew = true;
    }
    Date date2 = new Date();
    long difference = date2.getTime() - date1.getTime();

    assertThat(difference).isLessThan(2000);
    assertThat(difference).isGreaterThanOrEqualTo(1000);
    assertThat(val).isEqualTo(3);
    assertThat(candidateThrew).isEqualTo(false);
  }

  @Test
  public void controlRunsOnMainThreadCustomExecutorService() throws Exception {
    String threadName = "main";
    ThreadFactory threadFactory = runnable -> new Thread(runnable, threadName);
    Experiment<String> exp = new ExperimentBuilder<String>()
        .withName("test")
        .withMetricsProvider(new NoopMetricsProvider())
        .withExecutorService(Executors.newFixedThreadPool(4, threadFactory))
        .build();
    Callable<String> getThreadName = () -> Thread.currentThread().getName();

    String val = exp.runAsyncCandidateOnly(getThreadName, getThreadName);

    assertThat(val).isEqualTo(threadName);
  }

  @Test
  public void raiseOnMismatchRunsSlower() throws Exception {
    Experiment<Integer> raisesOnMismatch = new Experiment<>("raise", true, new NoopMetricsProvider());
    Experiment<Integer> doesNotRaiseOnMismatch = new Experiment<>("does not raise", new NoopMetricsProvider());
    final long raisesExecutionTime = timeExperiment(raisesOnMismatch);
    final long doesNotRaiseExecutionTime = timeExperiment(doesNotRaiseOnMismatch);

    assertThat(raisesExecutionTime).isGreaterThan(doesNotRaiseExecutionTime);
    assertThat(raisesExecutionTime).isGreaterThan(1000);
    assertThat(doesNotRaiseExecutionTime).isLessThan(200);
  }

  private long timeExperiment(final Experiment<Integer> exp) throws Exception {
    Date date1 = new Date();
    exp.runAsyncCandidateOnly(this::shortSleepFunction, this::sleepFunction);
    Date date2 = new Date();
    return date2.getTime() - date1.getTime();
  }

}
