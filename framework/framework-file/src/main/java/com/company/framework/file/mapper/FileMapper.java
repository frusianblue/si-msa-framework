package com.company.framework.file.mapper;

import com.company.framework.file.domain.FileMetadata;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileMapper {
    int insert(FileMetadata meta);

    Optional<FileMetadata> findById(@Param("id") Long id);

    int delete(@Param("id") Long id);
}
