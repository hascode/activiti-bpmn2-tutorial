package it;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.Message;
import javax.mail.internet.MimeMessage;

import org.activiti.engine.FormService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricFormProperty;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

public class IssueRequestProcessTest {
	private static final String DESCRIPTION_VALUE = "When I'm adding articles to the basket and click on 'buy' I'm getting a 404 error. I hate your fucking shop!";
	private static final String DESCRIPTION_KEY = "description";
	private static final String SUMMARY_VALUE = "Website Error! Shop order failed";
	private static final String SUMMARY_KEY = "summary";

	File issueTracker = new File(
			new File(System.getProperty("java.io.tmpdir")), "issues.txt");

	GreenMail smtpServer = new GreenMail(ServerSetupTest.SMTP);

	@Rule
	public ActivitiRule activitiRule = new ActivitiRule(
			"activiti-test.inmemory-cfg.xml");

	@Before
	public void setUp() {
		smtpServer.start();
	}

	@After
	public void tearDown() {
		smtpServer.stop();
	}

	@Test
	@Deployment(resources = "diagrams/IssueRequestProcess.bpmn")
	public void shouldProcessCriticalIssueRequest() throws Exception {
		// get a handle on the identity-service
		IdentityService identityService = activitiRule.getIdentityService();

		// create a new user to create a new request
		User requester = identityService.newUser("Micha Kops");
		identityService.saveUser(requester);

		// create group service and assign the user to it
		Group serviceGroup = identityService.newGroup("service");
		identityService.saveGroup(serviceGroup);
		identityService.createMembership(requester.getId(),
				serviceGroup.getId());

		// create a new user for an it-support employee
		User itguy = identityService.newUser("itguy");
		identityService.saveUser(itguy);

		// create a group it-support for critical issues
		Group itSupportGroup = identityService.newGroup("itsupport-critical");
		itSupportGroup.setName("IT Support for Critical Issues");
		identityService.saveGroup(itSupportGroup);

		// assign the user itguy to the group itsupport-critical
		identityService.createMembership(itguy.getId(), itSupportGroup.getId());

		// set requester as current user
		identityService.setAuthenticatedUserId(requester.getId());

		// assert that the process definition does exist in the current
		// environment
		ProcessDefinition definition = activitiRule.getRepositoryService()
				.createProcessDefinitionQuery()
				.processDefinitionKey("issueRequestProcess").singleResult();
		assertThat(definition, notNullValue());

		// get a handle on the form-service
		FormService formService = activitiRule.getFormService();

		// assert that our start form has four form fields
		List<FormProperty> formProps = formService.getStartFormData(
				definition.getId()).getFormProperties();
		assertThat(formProps.size(), equalTo(4));

		// fill out the first form's fields
		Map<String, String> requestFormProps = new HashMap<String, String>();
		requestFormProps.put(SUMMARY_KEY, SUMMARY_VALUE);
		requestFormProps.put(DESCRIPTION_KEY, DESCRIPTION_VALUE);
		requestFormProps.put("email", "someguy@hascode.com");
		requestFormProps.put("priority", "critical");

		Date startDate = new Date();

		// create a new process instance with given form params
		ProcessInstance processInstance = formService.submitStartFormData(
				definition.getId(), requestFormProps);
		assertThat(processInstance, notNullValue());

		// test the audit process, fetch historic data
		List<HistoricDetail> historicFormProps = activitiRule
				.getHistoryService().createHistoricDetailQuery()
				.formProperties().orderByVariableName().asc().list();

		// assert that the historic data corresponds to the form data that we've
		// entered
		assertThat(historicFormProps.size(), equalTo(4));
		HistoricFormProperty historicSummary = (HistoricFormProperty) historicFormProps
				.get(0);
		assertThat(historicSummary.getPropertyId(), equalTo(DESCRIPTION_KEY));
		assertThat(historicSummary.getPropertyValue(),
				equalTo(DESCRIPTION_VALUE));
		assertThat(historicSummary.getTime(), greaterThan(startDate));

		// assert that the bad-words filter has filtered one bad word and
		// replaced it with 'xxx'
		assertThat(
				(String) activitiRule.getRuntimeService()
						.getVariable(processInstance.getProcessInstanceId(),
								DESCRIPTION_KEY),
				endsWith("I hate your xxxing shop!"));

		// get a handle on the task service
		TaskService taskService = activitiRule.getTaskService();

		// seach for a task for candidate-group 'itsupport-critical'
		Task approveCriticalIssueTask = taskService.createTaskQuery()
				.processInstanceId(processInstance.getProcessInstanceId())
				.taskCandidateGroup(itSupportGroup.getId()).singleResult();
		assertThat(approveCriticalIssueTask.getName(),
				equalTo("Approve Critical Issue"));

		// claim the task for the user 'itguy'
		taskService.claim(approveCriticalIssueTask.getId(), itguy.getId());

		// approve the request and complete the task
		Map<String, Object> taskParams = new HashMap<String, Object>();
		taskParams.put("requestApproved", "true");
		taskService.complete(approveCriticalIssueTask.getId(), taskParams);

		// now we should have received an email..
		smtpServer.waitForIncomingEmail(5000L, 1);
		MimeMessage[] messages = smtpServer.getReceivedMessages();
		assertThat(messages.length, equalTo(1));
		MimeMessage mail = messages[0];

		// verify email content
		assertThat(mail.getSubject(), equalTo("Your inquiry regarding "
				+ SUMMARY_VALUE));
		assertThat((String) mail.getContent(), startsWith("Hello Micha Kops,"));
		assertThat((String) mail.getContent(), containsString(SUMMARY_VALUE));
		assertThat(mail.getRecipients(Message.RecipientType.TO)[0].toString(),
				equalTo("\"someguy@hascode.com\" <someguy@hascode.com>"));

		// assert that the java service-task has written output to the file
		assertThat(issueTracker.exists(), equalTo(true));
		assertThat(
				FileUtils.readFileToString(issueTracker),
				endsWith("New issue: Website Error! Shop order failed Description: When I'm adding articles to the basket and click on 'buy' I'm getting a 404 error. I hate your xxxing shop! Priority: critical"));
	}
}
