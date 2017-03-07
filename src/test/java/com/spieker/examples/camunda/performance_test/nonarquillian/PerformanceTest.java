package com.spieker.examples.camunda.performance_test.nonarquillian;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.migration.MigrationPlanBuilder;
import org.camunda.bpm.engine.migration.MigrationPlanExecutionBuilder;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class PerformanceTest {

    static {
        LogFactory.useSlf4jLogging(); // MyBatis
    }

    private ProcessEngine engine;

    @Before
    public void setup() {
        engine = ProcessEngineConfiguration.createProcessEngineConfigurationFromResourceDefault().setDatabaseSchemaUpdate("true").buildProcessEngine();
    }

    @BeforeClass
    public static void prepare() throws IOException {
        System.out.println("Removing database...");
        File targetFolder = new File("target/testDb");
        if (targetFolder.exists()) {
            String[] children = targetFolder.list();
            if (children != null) {
                for (String child : children) {
                    File file = new File(targetFolder, child);
                    if (!file.delete()) {
                        throw new IOException("Cannot delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }

    @Test
    public void testFromVersion1To2() {
        int fromVersion = 0;
        int toVersion = 1;

        createDeployment();
        createDeployment();

        List<ProcessDefinition> processDefinitions = engine.getRepositoryService().createProcessDefinitionQuery().orderByProcessDefinitionVersion().asc().list();

        startInstances(processDefinitions.get(fromVersion).getId());

        List<String> instanceIds = queryInstances(fromVersion, processDefinitions);

        migrateInstances(fromVersion, toVersion, processDefinitions, instanceIds);
    }

    private void migrateInstances(int fromVersion, int toVersion, List<ProcessDefinition> processDefinitions, List<String> instanceIds) {
        System.out.println("Found instances to migrate: " + instanceIds.size());
        System.out.print("Migrating instances...");

        MigrationPlanBuilder planBuilder = engine.getRuntimeService().createMigrationPlan(processDefinitions.get(fromVersion).getId(), processDefinitions.get(toVersion).getId());
        MigrationPlan plan = planBuilder.mapEqualActivities().updateEventTriggers().build();
        MigrationPlanExecutionBuilder migration = engine.getRuntimeService().newMigration(plan);

        long start = System.currentTimeMillis();
        migration.processInstanceIds(instanceIds).execute();
        System.out.println("finished in (ms): " + (System.currentTimeMillis() - start));
    }

    private List<String> queryInstances(int fromVersion, List<ProcessDefinition> processDefinitions) {
        System.out.print("Getting instances...");
        long start = System.currentTimeMillis();
        List<ProcessInstance> processInstances = engine.getRuntimeService().createProcessInstanceQuery().processDefinitionId(processDefinitions.get(fromVersion).getId()).list();
        System.out.println("finished in (ms): " + (System.currentTimeMillis() - start));
        return processInstances.stream().map(ProcessInstance::getProcessInstanceId).collect(Collectors.toList());
    }

    private void startInstances(String id) {
        System.out.print("Starting instances...");
        for (int i = 0; i < 50000; i++) {
            engine.getRuntimeService().startProcessInstanceById(id);
        }
        System.out.println("finished");
    }

    private org.camunda.bpm.engine.repository.Deployment createDeployment() {
        org.camunda.bpm.engine.repository.Deployment deployment = engine.getRepositoryService().createDeployment().addClasspathResource("process.bpmn").deploy();
        return deployment;
    }
}
