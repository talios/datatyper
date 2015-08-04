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

    assertThat(req.toString()).startsWith("GET{path=/api/story/32");
    assertThat(req).isInstanceOf(Request.class);
    assertThat(req).isInstanceOf(Request.GET.class);

    // Test for total matching

    int pathLength = req.match(new Request.Matcher<Integer>() {
      @Override
      public Integer match(Request.GET GET) {
        return GET.path().length();
      }

      @Override
      public Integer match(Request.DELETE DELETE) {
        return DELETE.path().length();
      }

      @Override
      public Integer match(Request.POST POST) {
        return POST.path().length();
      }
    });

    // Test for fluent matching
    Request.Matching<String> matching = req.<String>matching().GET(get -> "Hello");
    assertThat(matching.get()).isEqualTo("Hello");

    // Test for failed matching
    try {
      matching = req.<String>matching().DELETE(get -> "Hello");
      assertThat(matching.get()).isEqualTo("Hello");
      throw new AssertionError("This should throw.");
    } catch (IllegalStateException e) {
      // expected
    }

    // Test for total matching
    String returnValue = req.<String>matching().POST(post -> "Hello")
                            .orElse("Goodbye");

    assertThat(returnValue).isEqualTo("Goodbye");

    assertThat(pathLength).isEqualTo(13);

    req.shout();

  }

}
