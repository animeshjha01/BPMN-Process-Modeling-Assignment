package edu.srmist.outpass.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class OutpassController {

    private final CamundaClient camundaClient;

    public OutpassController(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    @PostMapping("/outpass")
    public Map<String, Object> applyOutpass(@RequestBody Map<String, Object> request) {
        ProcessInstanceEvent instance = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("hostel-outpass")
                .latestVersion()
                .variables(request)
                .send().join();
        return Map.of("processInstanceKey", instance.getProcessInstanceKey());
    }
}