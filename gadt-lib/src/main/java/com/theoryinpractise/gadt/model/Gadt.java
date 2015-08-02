package com.theoryinpractise.gadt.model;

import java.util.List;
import java.util.Objects;

public final class Gadt {

  private String name;

  private String packageName;

  private List<DataType> dataTypes;

  private List<String> implememts;

  public Gadt(String name, String packageName, List<DataType> dataTypes, List<String> implememts) {
    this.name = name;
    this.packageName = packageName;
    this.dataTypes = dataTypes;
    this.implememts = implememts;
  }

  public String name() {
    return name;
  }

  public String packageName() {
    return packageName;
  }

  public List<DataType> dataTypes() {
    return dataTypes;
  }

  public List<String> implememts() {
    return implememts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Gadt gadt = (Gadt) o;
    return Objects.equals(name, gadt.name) &&
        Objects.equals(packageName, gadt.packageName) &&
        Objects.equals(dataTypes, gadt.dataTypes) &&
        Objects.equals(implememts, gadt.implememts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, packageName, dataTypes, implememts);
  }

}
