package edu.srmist.outpass;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = {
    "classpath:bpmn/hostel-outpass.bpmn", 
    "classpath:bpmn/warden-approval.form"
})
public class OutpassWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutpassWorkflowApplication.class, args);
    }
}