package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.comment.Comment;
import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves each comment's {@code authorName} from its {@code authorId} (spec 005 FR-015). A
 * Spring-managed bean (not a static utility) because it now needs {@link UserRepository}.
 */
@Component
public class CommentMapper {

  private final UserRepository userRepository;

  public CommentMapper(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public CommentResponse toResponse(Comment comment) {
    String authorName =
        userRepository
            .findById(comment.getAuthorId())
            .map(user -> user.getName())
            .orElse(null);
    return new CommentResponse(
        comment.getId(), comment.getTicketId(), comment.getContent(), authorName,
        comment.getCreatedAt());
  }

  /**
   * Batched equivalent of mapping {@link #toResponse} over a list — resolves every comment's
   * author in a single {@code findAllById} instead of one lookup per comment, avoiding an N+1
   * query pattern when listing a ticket's comments.
   */
  public List<CommentResponse> toResponseList(List<Comment> comments) {
    Set<UUID> authorIds = new HashSet<>();
    for (Comment comment : comments) {
      if (comment.getAuthorId() != null) {
        authorIds.add(comment.getAuthorId());
      }
    }
    Map<UUID, String> namesById = new HashMap<>();
    for (User user : userRepository.findAllById(authorIds)) {
      namesById.put(user.getId(), user.getName());
    }
    return comments.stream()
        .map(
            comment ->
                new CommentResponse(
                    comment.getId(),
                    comment.getTicketId(),
                    comment.getContent(),
                    namesById.get(comment.getAuthorId()),
                    comment.getCreatedAt()))
        .toList();
  }
}
