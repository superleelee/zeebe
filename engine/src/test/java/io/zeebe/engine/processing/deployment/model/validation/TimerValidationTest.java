/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import static io.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;
import static org.junit.Assert.fail;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.ExpressionProcessor.VariablesLookup;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.ValidationVisitor;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class TimerValidationTest {

  private static final String INVALID_EXPRESSION = "?!";
  private static final String INVALID_EXPRESSION_MESSAGE =
      "failed to parse expression '?!': [1.2] failure: end of input expected\n"
          + "\n"
          + "?!\n"
          + " ^";

  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        // timer start event with invalid cycle
        Bpmn.createExecutableProcess("process").startEvent().timerWithCycle("foo").done(),
        Arrays.asList(
            expect(
                StartEvent.class,
                "Invalid timer cycle expression (Repetition spec must start with R)"))
      },
      {
        // timer start event with cycle expression that references a variable
        Bpmn.createExecutableProcess("process").startEvent().timerWithCycleExpression("x").done(),
        Arrays.asList(
            expect(
                StartEvent.class,
                "Invalid timer cycle expression (failed to evaluate expression 'x': no variable found for name 'x')"))
      },
      {
        // timer start event with not parsable cycle expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithCycleExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(
            expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE),
            expect(
                StartEvent.class,
                "Invalid timer cycle expression (" + INVALID_EXPRESSION_MESSAGE + ")"))
      },
      {
        // timer start event with invalid date
        Bpmn.createExecutableProcess("process").startEvent().timerWithDate("foo").done(),
        Arrays.asList(
            expect(
                StartEvent.class, "Invalid timer date expression (Invalid date-time format 'foo')"))
      },
      {
        // timer start event with invalid date expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithDateExpression("\"foo\"")
            .done(),
        Arrays.asList(
            expect(
                StartEvent.class, "Invalid timer date expression (Invalid date-time format 'foo')"))
      },
      {
        // timer start event with date expression that references a variable
        Bpmn.createExecutableProcess("process").startEvent().timerWithDateExpression("x").done(),
        Arrays.asList(
            expect(
                StartEvent.class,
                "Invalid timer date expression (failed to evaluate expression 'x': no variable found for name 'x')"))
      },
      {
        // timer start event with not parsable date expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithDateExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(
            expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE),
            expect(
                StartEvent.class,
                "Invalid timer date expression (" + INVALID_EXPRESSION_MESSAGE + ")"))
      },
      {
        // timer boundary event with invalid cycle
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent()
            .timerWithCycle("foo")
            .done(),
        Arrays.asList(
            expect(
                BoundaryEvent.class,
                "Invalid timer cycle expression (Repetition spec must start with R)"))
      },
      {
        // timer boundary event with not parsable cycle expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent()
            .timerWithCycleExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // timer boundary event with invalid duration
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent()
            .timerWithDuration("foo")
            .done(),
        Arrays.asList(
            expect(
                BoundaryEvent.class,
                "Invalid timer duration expression (Invalid duration format 'foo')"))
      },
      {
        // timer boundary event with not parsable duration expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent()
            .timerWithDurationExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // timer boundary event with invalid date
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent()
            .timerWithDate("foo")
            .done(),
        Arrays.asList(
            expect(
                BoundaryEvent.class,
                "Invalid timer date expression (Invalid date-time format 'foo')"))
      },
      {
        // timer boundary event with not parsable date expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent()
            .timerWithDateExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // timer intermediate event with invalid cycle
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithCycle("foo")
            .done(),
        Arrays.asList(
            expect(
                IntermediateCatchEvent.class,
                "Invalid timer cycle expression (Repetition spec must start with R)"))
      },
      {
        // timer intermediate event with not parsable cycle expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithCycleExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // timer intermediate event with invalid duration
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithDuration("foo")
            .done(),
        Arrays.asList(
            expect(
                IntermediateCatchEvent.class,
                "Invalid timer duration expression (Invalid duration format 'foo')"))
      },
      {
        // timer intermediate event with not parsable duration expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithDurationExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // timer intermediate event with invalid date
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithDate("foo")
            .done(),
        Arrays.asList(
            expect(
                IntermediateCatchEvent.class,
                "Invalid timer date expression (Invalid date-time format 'foo')"))
      },
      {
        // timer intermediate event with not parsable date expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithDateExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
    };
  }

  private static ValidationResults validate(final BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ExpressionLanguage expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage();
    final VariablesLookup emptyLookup = (name, scopeKey) -> null;
    final var expressionProcessor = new ExpressionProcessor(expressionLanguage, emptyLookup);
    final ValidationVisitor visitor =
        new ValidationVisitor(
            ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor));
    walker.walk(visitor);

    return visitor.getValidationResult();
  }

  @Before
  public void prepareModel() {
    if (modelSource instanceof BpmnModelInstance) {
      modelInstance = (BpmnModelInstance) modelSource;
    } else if (modelSource instanceof String) {
      final InputStream modelStream =
          TimerValidationTest.class.getResourceAsStream((String) modelSource);
      modelInstance = Bpmn.readModelFromStream(modelStream);
    } else {
      throw new RuntimeException("Cannot convert parameter to bpmn model");
    }
  }

  @Test
  public void validateModel() {
    // when
    final ValidationResults results = validate(modelInstance);

    Bpmn.validateModel(modelInstance);

    // then
    final List<ExpectedValidationResult> unmatchedExpectations = new ArrayList<>(expectedResults);
    final List<ValidationResult> unmatchedResults =
        results.getResults().values().stream()
            .flatMap(l -> l.stream())
            .collect(Collectors.toList());

    match(unmatchedResults, unmatchedExpectations);

    if (!unmatchedResults.isEmpty() || !unmatchedExpectations.isEmpty()) {
      failWith(unmatchedExpectations, unmatchedResults);
    }
  }

  private void match(
      final List<ValidationResult> unmatchedResults,
      final List<ExpectedValidationResult> unmatchedExpectations) {
    final Iterator<ExpectedValidationResult> expectationIt = unmatchedExpectations.iterator();

    outerLoop:
    while (expectationIt.hasNext()) {
      final ExpectedValidationResult currentExpectation = expectationIt.next();
      final Iterator<ValidationResult> resultsIt = unmatchedResults.iterator();

      while (resultsIt.hasNext()) {
        final ValidationResult currentResult = resultsIt.next();
        if (currentExpectation.matches(currentResult)) {
          expectationIt.remove();
          resultsIt.remove();
          continue outerLoop;
        }
      }
    }
  }

  private void failWith(
      final List<ExpectedValidationResult> unmatchedExpectations,
      final List<ValidationResult> unmatchedResults) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Not all expecations were matched by results (or vice versa)\n\n");
    describeUnmatchedExpectations(sb, unmatchedExpectations);
    sb.append("\n");
    describeUnmatchedResults(sb, unmatchedResults);
    fail(sb.toString());
  }

  private static void describeUnmatchedResults(
      final StringBuilder sb, final List<ValidationResult> results) {
    sb.append("Unmatched results:\n");
    results.forEach(
        e -> {
          sb.append(ExpectedValidationResult.toString(e));
          sb.append("\n");
        });
  }

  private static void describeUnmatchedExpectations(
      final StringBuilder sb, final List<ExpectedValidationResult> expectations) {
    sb.append("Unmatched expectations:\n");
    expectations.forEach(
        e -> {
          sb.append(e);
          sb.append("\n");
        });
  }
}
