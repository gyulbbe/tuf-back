package io.github.gyulbbe.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "users_seq_gen",
        sequenceName = "USERS_SEQ",
        allocationSize = 1
)
@Table(name = "USERS")
public class UserEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq_gen")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "NAME")
    private String name;

    @Column(name = "PHONE")
    private String phone;

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

    @Column(name = "coin")
    private Long coin;

    public void updateAdminProfile(String userId, String name, String race, String tier) {
        this.userId = userId;
        this.name = name;
        this.race = race;
        this.tier = tier;
    }

    public void updateStatus(String status) {
        this.status = status;
    }

    public void updateUserType(String userType) {
        this.userType = userType;
    }

    public void updatePassword(String password) {
        this.password = password;
    }
}
