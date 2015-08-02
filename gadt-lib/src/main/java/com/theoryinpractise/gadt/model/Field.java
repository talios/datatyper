package com.theoryinpractise.gadt.model;

import org.immutables.value.Value;

/**
 * Created by amrk on 2/08/15.
 */
@Value.Immutable
@Value.Style
public interface Field {

  @Value.Parameter
  String name();

  @Value.Parameter
  String type();

}
