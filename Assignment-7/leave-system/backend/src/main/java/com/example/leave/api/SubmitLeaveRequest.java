package com.example.leave.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SubmitLeaveRequest(
    @NotBlank String employeeName,
    @NotBlank @Email String employeeEmail,
    @NotBlank String leaveType,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    String reason
) {}