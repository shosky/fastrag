package com.fastrag.module.agent.context;

import lombok.Data;

import java.util.List;

/**
 * Simple user POJO used in the agent module.
 * TODO: Replace with the actual security User class once fastrag-security module provides one.
 */
@Data
public class User {

    private String uid;

    private String role;

    private String username;

    private List<String> departmentIds;
}
