package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.examples.Request;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test class is in a different package to generated files to check visibility constraints.
 */
public class TestGadt {

  @Test
  public void testUsingGadt() {

    Request req = Request.GET("/api/story/32");

    assertThat(req.toString()).isEqualTo("GET{path=/api/story/32}");
    assertThat(req).isInstanceOf(Request.class);
    assertThat(req).isInstanceOf(Request.GET.class);

  }

}
