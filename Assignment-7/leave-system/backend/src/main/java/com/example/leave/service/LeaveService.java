package com.example.leave.service;

import com.example.leave.api.DecisionRequest;
import com.example.leave.api.SubmitLeaveRequest;
import com.example.leave.domain.*;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.UserTask;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeaveService {

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);
    private static final int DEFAULT_ANNUAL_BALANCE = 20;

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final CamundaClient camunda;

    public LeaveService(
            LeaveRequestRepository requests,
            LeaveBalanceRepository balances,
            CamundaClient camunda) {
        this.requests = requests;
        this.balances = balances;
        this.camunda = camunda;
    }

    public LeaveRequest submit(SubmitLeaveRequest body) {
        balance(body.employeeEmail());

        int workingDays = calculateWorkingDays(body.startDate(), body.endDate());

        LeaveRequest entity = new LeaveRequest();
        entity.setEmployeeName(body.employeeName());
        entity.setEmployeeEmail(body.employeeEmail());
        entity.setLeaveType(body.leaveType());
        entity.setStartDate(body.startDate());
        entity.setEndDate(body.endDate());
        entity.setDays(workingDays);
        entity.setReason(body.reason());
        entity.setStatus(LeaveStatus.SUBMITTED);
        entity.setStatusMessage("Submitted, awaiting validation");
        entity = requests.save(entity);

        var event = camunda.newCreateInstanceCommand()
                .bpmnProcessId("leave-request")
                .latestVersion()
                .variables(Map.of(
                        "requestId", entity.getId(),
                        "employeeEmail", entity.getEmployeeEmail(),
                        "leaveType", entity.getLeaveType(),
                        "days", entity.getDays(),
                        "startDate", entity.getStartDate().toString(),
                        "endDate", entity.getEndDate().toString()
                ))
                .send()
                .join();

        requests.updateProcessInstanceKey(entity.getId(), event.getProcessInstanceKey());
        entity.setProcessInstanceKey(event.getProcessInstanceKey());
        return entity;
    }

    @Transactional
    public Map<String, Object> validate(Long requestId) {
        LeaveRequest req = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (req.getEndDate().isBefore(req.getStartDate())) {
            return Map.of(
                    "valid", false,
                    "validationMessage", "End date cannot be earlier than start date."
            );
        }

        if (req.getDays() <= 0) {
            return Map.of(
                    "valid", false,
                    "validationMessage", "Selected range contains no working days (weekend only)."
            );
        }

        if ("ANNUAL".equalsIgnoreCase(req.getLeaveType())) {
            LeaveBalance bal = balance(req.getEmployeeEmail());
            if (req.getDays() > bal.getRemainingDays()) {
                return Map.of(
                        "valid", false,
                        "validationMessage",
                        "Insufficient annual leave balance. Requested " + req.getDays()
                                + " day(s), remaining " + bal.getRemainingDays() + " day(s)."
                );
            }
        }

        req.setStatus(LeaveStatus.PENDING_APPROVAL);
        req.setStatusMessage("Awaiting manager approval");
        requests.save(req);

        return Map.of(
                "valid", true,
                "validationMessage", "OK"
        );
    }

    public void decide(Long id, DecisionRequest decision) {
        LeaveRequest req = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (req.getStatus() != LeaveStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not pending approval");
        }

        List<UserTask> tasks = camunda.newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(req.getProcessInstanceKey()))
                .send()
                .join()
                .items();

        if (tasks.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Manager approval user task not indexed yet; retry shortly.");
        }

        long userTaskKey = tasks.get(0).getUserTaskKey();

        camunda.newCompleteUserTaskCommand(userTaskKey)
                .variables(Map.of(
                        "approved", decision.approved(),
                        "managerComment", decision.comment() == null ? "" : decision.comment()
                ))
                .send()
                .join();
    }

    @Transactional
    public void finalizeRequest(
            Long requestId,
            boolean approved,
            String managerComment,
            String validationMessage) {
        LeaveRequest req = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        // Idempotency guard so retried jobs never deduct balance twice
        if (req.getStatus() == LeaveStatus.APPROVED || req.getStatus() == LeaveStatus.REJECTED) {
            log.info("Request #{} already finalized with status {}", requestId, req.getStatus());
            return;
        }

        if (approved) {
            if ("ANNUAL".equalsIgnoreCase(req.getLeaveType())) {
                LeaveBalance bal = balance(req.getEmployeeEmail());
                bal.setRemainingDays(Math.max(0, bal.getRemainingDays() - req.getDays()));
                balances.save(bal);
            }
            req.setStatus(LeaveStatus.APPROVED);
            req.setStatusMessage(
                    managerComment != null && !managerComment.isBlank()
                            ? managerComment
                            : "Approved by manager");
        } else {
            req.setStatus(LeaveStatus.REJECTED);
            if (validationMessage != null && !validationMessage.equals("OK") && !validationMessage.isBlank()) {
                req.setStatusMessage(validationMessage);
            } else if (managerComment != null && !managerComment.isBlank()) {
                req.setStatusMessage(managerComment);
            } else {
                req.setStatusMessage("Rejected by manager");
            }
        }

        requests.save(req);
        log.info("Notification: Leave request #{} for {} finalized as {} ({})",
                req.getId(), req.getEmployeeEmail(), req.getStatus(), req.getStatusMessage());
    }

    @Transactional
    public LeaveBalance balance(String email) {
        return balances.findById(email)
                .orElseGet(() -> balances.save(new LeaveBalance(email, DEFAULT_ANNUAL_BALANCE)));
    }

    private int calculateWorkingDays(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        int workingDays = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
        }
        return workingDays;
    }
}