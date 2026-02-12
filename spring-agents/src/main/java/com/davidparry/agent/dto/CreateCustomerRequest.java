package com.davidparry.agent.dto;

/**
 * Request DTO for creating a new customer.
 * 
 * @param name Customer name (required)
 * @param policyTypeName Policy type name (optional - defaults to "UNLIMITED")
 * @param defaultAllowance Default token allowance for all models (optional - null uses model defaults)
 * @param unlimited If true, set all model allowances to unlimited (optional - defaults to false)
 */
public record CreateCustomerRequest(
    String name,
    String policyTypeName,
    Long defaultAllowance,
    Boolean unlimited
) {}
