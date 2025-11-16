package io.github.gyulbbe.user.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "USERS")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "NAME")
    private String name;

    @Column(name = "TIER")
    private String tier;

    @Column(name = "RACE")
    private String race;

    @Column(name = "USER_TYPE")
    private String userType;

    @Column(name = "BATTLE_TAG")
    private String battleTag;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "PHOTO")
    private String photo;

    @Column(name = "POINT")
    private Long point;
}
