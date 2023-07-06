package com.honda.olympus.controller;



import java.io.IOException;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.honda.olympus.exception.FileProcessException;
import com.honda.olympus.exception.GenstaafeException;
import com.honda.olympus.service.GenstaafeService;
import com.honda.olympus.utils.GenstaafeConstants;
import com.honda.olympus.vo.GenAckResponseVO;
import com.honda.olympus.vo.MessageEventVO;
import com.honda.olympus.vo.MessageVO;
import com.honda.olympus.vo.ResponseVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@Validated
public class GenstaafeController {
	@Value("${service.success.message}")
	private String responseMessage;

	@Value("${service.name}")
	private String name;

	@Value("${service.version}")
	private String version;

	@Value("${service.profile}")
	private String profile;

	@Value("${service.name}")
	private String serviceName;
	@Autowired
	private GenstaafeService genackafeService;

	@PostMapping(path = "/event", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ResponseVO> generateAckowledment(@Valid @RequestBody MessageEventVO message)
			throws GenstaafeException, FileProcessException, IOException {
		log.debug(message.toString());
		
		
		GenAckResponseVO response = genackafeService.createStatus(message);
		if (response.getSuccess()) {
			return new ResponseEntity<>(new ResponseVO(serviceName,GenstaafeConstants.ONE_STATUS, responseMessage, response.getFileName()), HttpStatus.OK);
		}

		return new ResponseEntity<>(new ResponseVO(serviceName,GenstaafeConstants.ZERO_STATUS, 
				"No se encontraron líneas con suficiente información para ser procesadas ", response.getFileName()), HttpStatus.BAD_REQUEST);
		
	}

}
