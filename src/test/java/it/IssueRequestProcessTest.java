package it;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.FormService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricFormProperty;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.junit.Rule;
import org.junit.Test;

public class IssueRequestProcessTest {
	private static final String DESCRIPTION_VALUE = "When I'm adding articles to the basket and click on 'buy' I'm getting a 404 error. I hate your fucking shop!";
	private static final String DESCRIPTION_KEY = "description";
	private static final String SUMMARY_VALUE = "Website Error! Shop order failed";
	private static final String SUMMARY_KEY = "summary";
	@Rule
	public ActivitiRule activitiRule = new ActivitiRule(
			"activiti-test.inmemory-cfg.xml");

	@Test
	@Deployment(resources = "diagrams/IssueRequestProcess.bpmn")
	public void shouldProcessCriticalIssueRequest() {
		ProcessDefinition definition = activitiRule.getRepositoryService()
				.createProcessDefinitionQuery()
				.processDefinitionKey("issueRequestProcess").singleResult();
		assertThat(definition, notNullValue());

		FormService formService = activitiRule.getFormService();
		List<FormProperty> formProps = formService.getStartFormData(
				definition.getId()).getFormProperties();
		assertThat(formProps.size(), equalTo(4));

		Map<String, String> requestFormProps = new HashMap<String, String>();
		requestFormProps.put(SUMMARY_KEY, SUMMARY_VALUE);
		requestFormProps.put(DESCRIPTION_KEY, DESCRIPTION_VALUE);
		requestFormProps.put("email", "someguy@hascode.com");
		requestFormProps.put("priority", "critical");

		Date startDate = new Date();
		ProcessInstance processInstance = formService.submitStartFormData(
				definition.getId(), requestFormProps);
		assertThat(processInstance, notNullValue());

		List<HistoricDetail> historicFormProps = activitiRule
				.getHistoryService().createHistoricDetailQuery()
				.formProperties().orderByVariableName().asc().list();
		assertThat(historicFormProps.size(), equalTo(4));
		HistoricFormProperty historicSummary = (HistoricFormProperty) historicFormProps
				.get(0);
		assertThat(historicSummary.getPropertyId(), equalTo(DESCRIPTION_KEY));
		assertThat(historicSummary.getPropertyValue(),
				equalTo(DESCRIPTION_VALUE));
		assertThat(historicSummary.getTime(), greaterThan(startDate));

		assertThat(
				(String) activitiRule.getRuntimeService()
						.getVariable(processInstance.getProcessInstanceId(),
								DESCRIPTION_KEY),
				endsWith("I hate your xxxing shop!"));

		TaskService taskService = activitiRule.getTaskService();
		Task approveCriticalIssueTask = taskService.createTaskQuery()
				.processInstanceId(processInstance.getProcessInstanceId())
				.singleResult();
		assertThat(approveCriticalIssueTask.getName(),
				equalTo("Approve Critical Issue"));
		Map<String, Object> taskParams = new HashMap<String, Object>();
		taskParams.put("requestApproved", "true");
		taskService.complete(approveCriticalIssueTask.getId(), taskParams);
	}
}
