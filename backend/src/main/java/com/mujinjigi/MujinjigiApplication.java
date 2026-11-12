package com.mujinjigi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 0단계 · Spring Boot 프로젝트 생성
 * 서버를 켜는 시작 클래스. main()을 실행하면 Spring이 뜨고 8080 포트로 요청을 받음
 * 예: .\gradlew.bat bootRun → 이 클래스의 main()이 실행됨
 */
@SpringBootApplication
public class MujinjigiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MujinjigiApplication.class, args);
	}

}
