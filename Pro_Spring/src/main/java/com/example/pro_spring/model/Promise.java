package com.example.pro_spring.model;

public record Promise(
    boolean promised,
    int acceptedProposal,
    int acceptedValue
) {}
