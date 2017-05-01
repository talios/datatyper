package com.theoryinpractise.datatyper.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DataTypeContainer {

  private String name;

  private String packageName;

  private List<DataType> dataTypes;

  private List<String> imports;

  private Set<String> implememts;

  public DataTypeContainer(
      String name,
      String packageName,
      List<DataType> dataTypes,
      List<String> imports,
      Set<String> implememts) {
    this.name = name;
    this.packageName = packageName;
    this.dataTypes = dataTypes;
    this.imports = imports;
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

  public List<String> imports() {
    return imports;
  }

  public Set<String> implememts() {
    return implememts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DataTypeContainer dataTypeContainer = (DataTypeContainer) o;
    return Objects.equals(name, dataTypeContainer.name)
        && Objects.equals(packageName, dataTypeContainer.packageName)
        && Objects.equals(dataTypes, dataTypeContainer.dataTypes)
        && Objects.equals(imports, dataTypeContainer.imports)
        && Objects.equals(implememts, dataTypeContainer.implememts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, packageName, dataTypes, imports, implememts);
  }
}
