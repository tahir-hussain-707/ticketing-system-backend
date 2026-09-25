package com.frequency.ticketing.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import com.frequency.ticketing.domain.comment.Comment;
import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.domain.user.UserRole;
import com.frequency.ticketing.repository.UserRepository;
import com.frequency.ticketing.web.dto.CommentMapper;
import com.frequency.ticketing.web.dto.CommentResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentMapperTest {

  @Mock private UserRepository userRepository;

  @Test
  void toResponseMapsEveryField() {
    UUID ticketId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Comment comment = new Comment(ticketId, "hello world", authorId);
    when(userRepository.findById(authorId))
        .thenReturn(Optional.of(new User("Jane Doe", "jane@example.test", "hash", UserRole.GENERAL)));

    CommentResponse response = new CommentMapper(userRepository).toResponse(comment);

    assertThat(response.id()).isEqualTo(comment.getId());
    assertThat(response.ticketId()).isEqualTo(ticketId);
    assertThat(response.content()).isEqualTo("hello world");
    assertThat(response.authorName()).isEqualTo("Jane Doe");
  }

  @Test
  void toResponseListPreservesOrder() {
    UUID ticketId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Comment first = new Comment(ticketId, "first", authorId);
    Comment second = new Comment(ticketId, "second", authorId);
    User author = new User("Jane Doe", "jane@example.test", "hash", UserRole.GENERAL);
    when(userRepository.findAllById(anyIterable())).thenReturn(List.of(author));

    List<CommentResponse> responses =
        new CommentMapper(userRepository).toResponseList(List.of(first, second));

    assertThat(responses).extracting(CommentResponse::content).containsExactly("first", "second");
  }
}
