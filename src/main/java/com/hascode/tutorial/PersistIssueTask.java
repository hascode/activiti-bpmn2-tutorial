package com.hascode.tutorial;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;

public class PersistIssueTask implements JavaDelegate {
	private final File issueTracker = new File("/tmp/issues.txt");

	@Override
	public void execute(final DelegateExecution execution) throws Exception {
		String output = String.format(
				"(%s) Issue added: %s \tDescription: %s\tPriority: %s",
				execution.getVariable("summary"), new Date().toString(),
				execution.getVariable("description"),
				execution.getVariable("priority"));
		FileWriter writer = new FileWriter(issueTracker);
		writer.write(output);
		writer.close();
	}

}
