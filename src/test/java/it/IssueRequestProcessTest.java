package it;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.activiti.engine.FormService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.junit.Rule;
import org.junit.Test;

public class IssueRequestProcessTest {
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
	}
}
