package com.example.pro_spring.model;

public class Promise {

  public boolean promised;
  public int acceptedProposal;
  public int acceptedValue;

  public Promise(boolean promised, int acceptedProposal, int acceptedValue) {
    this.promised = promised;
    this.acceptedProposal = acceptedProposal;
    this.acceptedValue = acceptedValue;
  }
}
