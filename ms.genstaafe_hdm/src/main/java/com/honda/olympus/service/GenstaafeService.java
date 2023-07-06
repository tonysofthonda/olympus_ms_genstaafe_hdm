package com.honda.olympus.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.honda.olympus.dao.AfeColorEntity;
import com.honda.olympus.dao.AfeDivisionEntity;
import com.honda.olympus.dao.AfeEventStatusEntity;
import com.honda.olympus.dao.AfeEventStatusHistoryEntity;
import com.honda.olympus.dao.AfeFixedOrdersEvEntity;
import com.honda.olympus.dao.AfeModelColorEntity;
import com.honda.olympus.dao.AfeModelEntity;
import com.honda.olympus.dao.AfePlantEntity;
import com.honda.olympus.dao.EventCodeEntity;
import com.honda.olympus.exception.FileProcessException;
import com.honda.olympus.exception.GenstaafeException;
import com.honda.olympus.repository.AfeAckEvRepository;
import com.honda.olympus.repository.AfeActionRepository;
import com.honda.olympus.repository.AfeColorRepository;
import com.honda.olympus.repository.AfeDivisionRepository;
import com.honda.olympus.repository.AfeEventCodeRepository;
import com.honda.olympus.repository.AfeEventStatusHistoryRepository;
import com.honda.olympus.repository.AfeEventStatusRepository;
import com.honda.olympus.repository.AfeFixedOrdersEvRepository;
import com.honda.olympus.repository.AfeModelColorRepository;
import com.honda.olympus.repository.AfeModelRepository;
import com.honda.olympus.repository.AfeModelTypeRepository;
import com.honda.olympus.repository.AfePlantRepository;
import com.honda.olympus.utils.GenstaMessagesHandler;
import com.honda.olympus.utils.GenstaafeConstants;
import com.honda.olympus.utils.GenstaafeUtils;
import com.honda.olympus.vo.EventVO;
import com.honda.olympus.vo.GenAckResponseVO;
import com.honda.olympus.vo.MessageEventVO;
import com.honda.olympus.vo.MoveFileVO;
import com.honda.olympus.vo.TemplateFieldVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GenstaafeService {

	@Autowired
	LogEventService logEventService;

	@Autowired
	MovFileService movFileService;

	@Autowired
	private AfeFixedOrdersEvRepository afeFixedOrdersEvRepository;

	@Autowired
	private AfeModelColorRepository afeModelColorRepository;

	@Autowired
	private AfeModelRepository afeModelRepository;

	@Autowired
	AfeModelTypeRepository modelTypeRepository;

	@Autowired
	AfeColorRepository afeColorRepository;

	@Autowired
	AfeActionRepository afeActionRepository;

	@Autowired
	AfeAckEvRepository afeAckEvRepository;

	@Autowired
	AfeDivisionRepository afeDivisionRepository;

	@Autowired
	AfePlantRepository afePlantRepository;

	@Autowired
	AfeEventStatusRepository afeEventStatusRepository;

	@Autowired
	AfeEventCodeRepository afeEventCodeRepository;

	@Autowired
	AfeEventStatusHistoryRepository afeEventStatusHistoryRepository;

	@Value("${service.name}")
	private String serviceName;

	@Value("${folder.source}")
	private String folderSource;

	@Value("${template.control}")
	private Integer templateControl;

	@Value("{service.success.message}")
	private String successMessage;

	@Autowired
	GenstaMessagesHandler genstaMessagesHandler;

	List<TemplateFieldVO> templateData;

	private String output;

	public GenAckResponseVO createStatus(MessageEventVO message) throws FileProcessException, GenstaafeException {

		JSONObject template = GenstaafeUtils.validateFileTemplate(templateControl);
		this.templateData = GenstaafeUtils.readGenAckAfeFileTemplate(template);
		
		Boolean success = Boolean.FALSE;

		if (!message.getStatus().equals(GenstaafeConstants.ONE_STATUS)) {

			genstaMessagesHandler.createAndLogMessageStatusFail(message.getStatus());
			throw new GenstaafeException(
					"El mensaje tiene un status no aceptado para el proceso " + message.toString());

		}

		if (message.getDetails().isEmpty()) {

			genstaMessagesHandler.createAndLogMessageDetailFail(message.toString());
			throw new GenstaafeException("El mensaje no tiene detalles para procesar: " + message.toString());

		}

		//File name
		String fileName = GenstaafeUtils.getFileName();

		this.output = folderSource + "/" + fileName;

		Iterator<Long> it = message.getDetails().iterator();
		EventVO event;
		StringBuilder fileLine;

		while (it.hasNext()) {
			Long fixedOrderId = it.next();
			fileLine = new StringBuilder();

			// QUERY1
			List<AfeFixedOrdersEvEntity> fixedOrders = afeFixedOrdersEvRepository.findAllById(fixedOrderId);

			if (fixedOrders.isEmpty()) {

				genstaMessagesHandler.createAndLogMessageNoFixedOrders(fixedOrderId,
						"SELECT o FROM AfeFixedOrdersEvEntity o WHERE o.id = :id ");
				continue;
			}

			AfeFixedOrdersEvEntity fixedOrderQ1 = fixedOrders.get(0);
			Long modelIdQ1 = fixedOrderQ1.getModelColorId();
			Long fixedOrderIdQ1 = fixedOrderQ1.getId();
			Long chrgAsctQ1 = fixedOrderQ1.getChrgAsct();
			Date ordDueDateQ1 = fixedOrderQ1.getOrdDueDt();
			String orderNumberQ1 = fixedOrderQ1.getOrderNumber();
			String externConfigIdQ1 = fixedOrderQ1.getExternConfigId();
			String vinNumberQ1 = fixedOrderQ1.getVinNumber();
			String orderTypeQ1 = fixedOrderQ1.getOrderType();

			Date weekProdStartDayQ1 = fixedOrderQ1.getProdWeekStartDay();

			// QUERY2
			List<AfeModelColorEntity> modelColors = afeModelColorRepository.findAllByModelId(modelIdQ1);
			if (modelColors.isEmpty()) {
				log.debug("ProcessFile:: ModelColor no exists");
				genstaMessagesHandler.createAndLogMessageModelColorNoExists(modelIdQ1, fixedOrderIdQ1,
						"SELECT o FROM AfeModelColorEntity o WHERE o.modelId = :modelId ");
				// return to main line process loop
				continue;
			}

			AfeModelColorEntity modelColorQ2 = modelColors.get(0);

			Long colorIdQ2 = modelColorQ2.getColorId();
			Long modelIdQ2 = modelColorQ2.getModel_id();

			// QUERY3
			List<AfeModelEntity> models = afeModelRepository.findAllByModelId(modelIdQ2);
			if (models.isEmpty()) {
				log.debug("ProcessFile:: Model no exists");
				genstaMessagesHandler.createAndLogMessageModelNoExist(modelIdQ2, fixedOrderId,
						"SELECT o FROM AfeModelEntity o WHERE o.id = :id ");
				// return to main line process loop
				continue;
			}

			AfeModelEntity model =  models.get(0);
			Long modelTypeIdQ3 = model.getModelTypeId();
			Long plantIdQ3 = model.getPlantId();
			Long divisionIdQ3 = model.getDivisionId();
			String codeQ3 = model.getCode();

			String mdlTypeQ3 = "";
			
			if (modelTypeIdQ3 == 1) {
				mdlTypeQ3 = "KC";
			}

			if (modelTypeIdQ3 == 2) {
				mdlTypeQ3 = "KA";
			}


			// QUERY4
			Optional<AfeDivisionEntity> afeDivision = afeDivisionRepository.findById(divisionIdQ3);
			if (!afeDivision.isPresent()) {

				genstaMessagesHandler.createAndLogMessageDvisionNoExist(divisionIdQ3, fixedOrderId,
						"SELECT * FROM AFE_DIVISION WHERE ID = :divisionIdQ3 ");
				continue;
			}

			String abbrevationQ4 = afeDivision.get().getAbbreviation();

			// QUERY5
			Optional<AfePlantEntity> afePlant = afePlantRepository.findById(plantIdQ3);
			if (!afePlant.isPresent()) {

				genstaMessagesHandler.createAndLogMessagePlantNoExist(divisionIdQ3, fixedOrderId,
						"SELECT * FROM AFE_DIVISION WHERE ID = :plantIdQ3 ");
				continue;
			}

			String abbrevationQ5 = afePlant.get().getAbbreviation();

			// QUERY6
			List<AfeColorEntity> colors = afeColorRepository.findAllById(colorIdQ2);

			if (colors.isEmpty()) {

				genstaMessagesHandler.createAndLogMessageColorNoExists(colorIdQ2, fixedOrderId,
						"SELECT o FROM AfeColorEntity o WHERE o.id = :id");

				continue;

			}

			AfeColorEntity colorQ6 = colors.get(0);
			String colorCodeQ6 = colorQ6.getCode();
			String colorExtCodeQ6 = colorQ6.getExteriorCode();
			String colorIntCodeQ6 = colorQ6.getInteriorCode();

			// QUERY7
			List<AfeEventStatusEntity> eventStatuses = afeEventStatusRepository.findAllByFixedOrder(fixedOrderIdQ1);

			if (eventStatuses.isEmpty()) {
				log.debug("ProcessFile:: FixedOrder DOESN'T exist in EVENT_STATUS");
				genstaMessagesHandler.createAndLogMessageEventStatusNoExists(fixedOrderIdQ1,
						"SELECT o FROM AfeEventStatusEntity o WHERE o.fixedOrderId = :fixedOrderId ORDER BY o.id DESC");

				continue;

			}

			AfeEventStatusEntity eventStatus = eventStatuses.get(0);
			Long eventStatusIdQ7 = eventStatus.getId();
			Date estdDelvryDtQ7 = eventStatus.getEstdDelvryDt();
			Long eventCodeIdQ7 = eventStatus.getEventCodeId();
			

			// QUERY8
			List<AfeEventStatusHistoryEntity> statusHistory = afeEventStatusHistoryRepository.findAllByCode(eventStatusIdQ7);

			if (statusHistory.isEmpty()) {
				genstaMessagesHandler.createAndLogMessageEventStatusHistoryNoExists(fixedOrderIdQ1,
						"SELECT o FROM AfeEventStatusHistoryEntity o WHERE o.afeEventStatusId = :afeEventStatusId  ORDER BY  o.id ASC");

				// return to main line process loop
				continue;
			}

			AfeEventStatusHistoryEntity estatusHistory = statusHistory.get(0);

			Date curEvntStatusDteQ8 = estatusHistory.getCurEvntStatusDt();

			// QUERY9
			List<EventCodeEntity> eventCodes = afeEventCodeRepository.findAllByEventCode(eventCodeIdQ7);

			if (eventCodes.isEmpty()) {
				genstaMessagesHandler.createAndLogMessageEventCodeNoExists(fixedOrderIdQ1,
						"SELECT o FROM EventCodeEntity o WHERE o.id = :eventCodeId");

				continue;

			}

			EventCodeEntity eventCode = eventCodes.get(0); 
			Long eventCodeNumberQ9 = eventCode.getEventCodeNumber();
			String descriptionQ9 = eventCode.getDescription();

			try {

				fileLine.append(completeSpaces(abbrevationQ4.trim(), "XPROD-DIV-CD"));
				fileLine.append(completeSpaces(abbrevationQ5.trim(), "PLANT-ID"));
				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(weekProdStartDayQ1),
						"GM-PROD-WEEK-START-DAY"));
				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(ordDueDateQ1), "GM-ORD-DUE-DT"));
				fileLine.append(completeSpaces(codeQ3, "MDL-ID"));
				fileLine.append(completeSpaces(mdlTypeQ3, "MDL-TYP-CD"));
				fileLine.append("   "); // "MDL-OPT-PKG-CD"

				fileLine.append(completeSpaces(colorCodeQ6.trim(), "MDL-MFG-COLOR-ID"));
				fileLine.append(completeSpaces(colorExtCodeQ6.trim(), "EXTR-COLOR-CD"));
				fileLine.append(completeSpaces(colorIntCodeQ6.trim(), "INT-COLOR-CD"));

				fileLine.append(completeSpaces(orderNumberQ1.trim(), "GM-ORD-STA-VEH-ORD-NO"));

				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(estdDelvryDtQ7), "GM-ORD-STA-EXTERN-CONFIG-TYPES"));
				fileLine.append(completeSpaces(externConfigIdQ1, "GM-ORD-STA-EXTERN-CONFIG-ID"));

				fileLine.append(completeSpaces(vinNumberQ1, "GM-ORD-STA-VIN-NO"));

				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(estdDelvryDtQ7),
						"GM-ORD-STA-VO-LAST-CHG-TMSTP"));

				fileLine.append(completeSpaces(orderTypeQ1.trim(), "GM-ORD-STA-TYP-CD"));
				
				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(estdDelvryDtQ7),
						"GM-ORD-STA-TARGET-PRODN-DT"));
				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(estdDelvryDtQ7),
						"GM-ORD-STA-ESTD-DELVRY-DT"));

				fileLine.append(completeSpaces("" + eventCodeNumberQ9, "GM-ORD-STA-CURR-VEH-EVNT-CD"));

				fileLine.append(completeSpaces(GenstaafeUtils.formatDateTimeStamp(curEvntStatusDteQ8),
						"GM-ORD-STA-CURR_EVENT-STAT-DT"));

				fileLine.append(completeSpaces("" + chrgAsctQ1, "GM-ORD-STAK-DLR-REC-BUS-AST-CD"));
				fileLine.append(completeSpaces(descriptionQ9, "GM-ORD-ACK-CURR-VEH-EVNT-DESC"));

				log.debug("Length: " + fileLine.length());
				log.debug(fileLine.toString());

				fileLine = completeLineSpaces(fileLine, templateControl);

				try {

					addLineToFile(fileLine.toString());

				} catch (IOException e) {
					log.error("Line not added due to: {} ", e.getLocalizedMessage());
					continue;
				}

			} catch (GenstaafeException e) {
				log.info("El archivo " + fileName + " NO fue creado correctamente en la ubicación: " + folderSource);
				event = new EventVO(serviceName, GenstaafeConstants.ZERO_STATUS,
						"El archivo " + fileName + " NO fue creado correctamente en la ubicación: " + folderSource, "");
				logEventService.sendLogEvent(event);
				continue;
			}

			success = Boolean.TRUE;
			fileLine.setLength(0);

		}

		if (success) {
			log.debug("Genackafe:: Se creo correctamente el archivo {} en la ubicación:{} ", fileName, folderSource);
			event = new EventVO(serviceName, GenstaafeConstants.ZERO_STATUS,
					"Se creo correctamente el archivo " + fileName + " en la ubicación: " + folderSource, "");
			logEventService.sendLogEvent(event);
			movFileService.sendMoveFileMessage(new MoveFileVO(1L, successMessage, fileName));
			return new GenAckResponseVO(success, fileName);
		}

		return new GenAckResponseVO(success, fileName);

	}

	private String completeSpaces(String value, String templeateField) throws GenstaafeException {

		StringBuilder spaces = new StringBuilder();
		Optional<TemplateFieldVO> field;
		field = GenstaafeUtils.getTemplateValueOfField(this.templateData, templeateField);
		if (field.isPresent()) {
			if (field.get().total != value.length()) {
				if (field.get().total > value.length()) {
					// complete
					spaces.append(value);
					int difference = field.get().total - value.length();

					for (int i = 0; i < difference; i++) {
						spaces.append(" ");
					}
				} else {
					// error
					log.info("Filed incorrect: {} with value: {}", templeateField, value);
					throw new GenstaafeException("Filed incorrect: " + templeateField + " with value: " + value);
				}
			} else {
				spaces.append(value);
			}

			return spaces.toString();
		}

		throw new GenstaafeException("Filed incorrect: " + templeateField + " with value: " + value);
	}

	private StringBuilder completeLineSpaces(StringBuilder line, Integer templateControl) {

		if (line.length() < templateControl) {
			Integer difference = templateControl - line.length();
			for (Integer i = 0; i < difference; i++) {

				line.append(" ");
			}
			log.info("Line completed with {} characters", difference);
		}

		return line;

	}

	private void addLineToFile(String line) throws IOException {
		Path folder = Paths.get(folderSource);

		if (!Files.exists(folder)) {

			Files.createDirectories(folder);

		}

		String newLine = line + "\n";
		final Path path = Paths.get(this.output);

		if (path == null) {
			Files.createFile(path);
		}

		Files.write(path, newLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

	}

}
