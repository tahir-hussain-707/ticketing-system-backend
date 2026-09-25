package com.frequency.ticketing.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record CommentPage(
    List<CommentResponse> content, int page, int size, long totalElements, int totalPages) {

  public static CommentPage from(Page<CommentResponse> page) {
    return new CommentPage(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
