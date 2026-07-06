package com.fastrag.module.agent.context;

import lombok.Data;

import java.util.List;

@Data
public class ConfigurableItem {

    private String field;

    private String name;

    private String type;

    private String kind;

    private String description;

    private Object defaultValue;

    private List<Option> options;

    private String auth;

    @Data
    public static class Option {

        private String key;

        private String name;

        private String description;
    }
}
