package com.frequency.ticketing.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.web.dto.CommentPage;
import com.frequency.ticketing.web.dto.CommentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CommentPageTest {

  @Test
  void fromMapsEveryFieldFromSpringPage() {
    CommentResponse c1 =
        new CommentResponse(UUID.randomUUID(), UUID.randomUUID(), "one", "Author One", Instant.now());
    CommentResponse c2 =
        new CommentResponse(UUID.randomUUID(), UUID.randomUUID(), "two", "Author Two", Instant.now());
    var springPage = new PageImpl<>(List.of(c1, c2), PageRequest.of(1, 2), 5);

    CommentPage page = CommentPage.from(springPage);

    assertThat(page.content()).containsExactly(c1, c2);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(3);
  }

  @Test
  void fromEmptyPageHasEmptyContentNotNull() {
    var springPage = new PageImpl<CommentResponse>(List.of(), PageRequest.of(0, 20), 0);

    CommentPage page = CommentPage.from(springPage);

    assertThat(page.content()).isNotNull().isEmpty();
    assertThat(page.totalElements()).isZero();
  }
}
