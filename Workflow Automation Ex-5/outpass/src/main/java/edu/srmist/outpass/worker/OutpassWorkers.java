package edu.srmist.outpass.worker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Component
public class OutpassWorkers {

    @JobWorker(type = "check-outpass")
    public Map<String, Object> checkOutpass(@Variable String outDate, @Variable String returnDate) {
        LocalDate out = LocalDate.parse(outDate);
        LocalDate ret = LocalDate.parse(returnDate);
        
        long nights = ChronoUnit.DAYS.between(out, ret);
        boolean valid = !ret.isBefore(out); 
        boolean approved = valid && (nights == 0); // Auto-approve if it's a day trip
        
        return Map.of("nights", nights, "valid", valid, "approved", approved);
    }

    @JobWorker(type = "issue-outpass")
    public Map<String, Object> issueOutpass(
            @Variable String regNo, 
            @Variable String outDate, 
            @Variable String studentName,
            @Variable String parentPhone, 
            @Variable Boolean approved, 
            ActivatedJob job) {
            
        // Warden remarks might be null if auto-approved, so we fetch safely from the job variables
        String wardenRemarks = (String) job.getVariablesAsMap().getOrDefault("wardenRemarks", "N/A");
        
        String status = Boolean.TRUE.equals(approved) ? "ISSUED" : "REJECTED";
        String passNumber = Boolean.TRUE.equals(approved) ? "OP-" + regNo + "-" + outDate : "N/A";
        
        System.out.printf("SMS to parent (%s): %s's out-pass is %s. Remarks: %s%n", 
                parentPhone, studentName, status, wardenRemarks);
                
        return Map.of("passStatus", status, "passNumber", passNumber);
    }
}