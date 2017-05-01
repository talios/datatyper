package com.theoryinpractise.datatyper.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DataSetSet {

  private String name;

  private String packageName;

  private List<DataType> dataTypes;

  private List<String> imports;

  private Set<String> implememts;

  public DataSetSet(
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
    DataSetSet dataSetSet = (DataSetSet) o;
    return Objects.equals(name, dataSetSet.name)
        && Objects.equals(packageName, dataSetSet.packageName)
        && Objects.equals(dataTypes, dataSetSet.dataTypes)
        && Objects.equals(imports, dataSetSet.imports)
        && Objects.equals(implememts, dataSetSet.implememts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, packageName, dataTypes, imports, implememts);
  }
}
