package com.theoryinpractise.gadt.model;

import org.immutables.value.Value;

import java.util.List;

/**
 * Created by amrk on 2/08/15.
 */
@Value.Immutable
@Value.Style
public interface DataType {

  @Value.Parameter
  String name();

  @Value.Parameter
  List<Field> fields();

}
