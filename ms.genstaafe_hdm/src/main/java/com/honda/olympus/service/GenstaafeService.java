package com.honda.olympus.service;

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
import com.honda.olympus.dao.AfeEventStatusHistoryEntity;
import com.honda.olympus.dao.AfeFixedOrdersEvEntity;
import com.honda.olympus.dao.AfeModelColorEntity;
import com.honda.olympus.dao.AfeModelEntity;
import com.honda.olympus.dao.AfePlantEntity;
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
import com.honda.olympus.utils.GenackafeUtils;
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

	public GenAckResponseVO createStatus(MessageEventVO message) throws FileProcessException, GenstaafeException {

		JSONObject template = GenstaafeUtils.validateFileTemplate(templateControl);
		this.templateData = GenstaafeUtils.readGenAckAfeFileTemplate(template);

		final DateTimeFormatter currentDateFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd");
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

		String fileName = GenstaafeUtils.getFileName();

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
				break;
			}

			AfeFixedOrdersEvEntity fixedOrderQ1 = fixedOrders.get(0);
			Long Q1modelId = fixedOrderQ1.getModelColorId();
			Long Q1fixedOrderId = fixedOrderQ1.getId();
			Date Q1weekStartDay = fixedOrderQ1.getProdWeekStartDay();
			Date Q1ordDueDate = fixedOrderQ1.getOrdDueDt();
			String q1OrderNumber = fixedOrderQ1.getOrderNumber();
			String externConfigIdQ1 = fixedOrderQ1.getExternConfigId();
			String vinNumberQ1 = fixedOrderQ1.getVinNumber();
			String orderTypeQ1 = fixedOrderQ1.getOrderType();

			// QUERY2
			List<AfeModelColorEntity> modelColors = afeModelColorRepository.findAllByModelId(Q1modelId);
			if (modelColors.isEmpty()) {
				log.debug("ProcessFile:: ModelColor no exists");
				genstaMessagesHandler.createAndLogMessageModelColorNoExists(Q1modelId, Q1fixedOrderId,
						"SELECT o FROM AfeModelColorEntity o WHERE o.modelId = :modelId ");
				// return to main line process loop
				break;
			}

			AfeModelColorEntity modelColorQ2 = modelColors.get(0);

			Long colorIdQ2 = modelColorQ2.getColorId();

			// QUERY3
			List<AfeModelEntity> models = afeModelRepository.findAllByModelId(Q1modelId);
			if (models.isEmpty()) {
				log.debug("ProcessFile:: Model no exists");
				genstaMessagesHandler.createAndLogMessageModelNoExist(modelCode,
						"SELECT o FROM AfeModelEntity o WHERE o.id = :id ");
				// return to main line process loop
				break;
			}

			Long modelIdQ3 = models.get(0).getId();
			Long modelTypeIdQ3 = models.get(0).getModelTypeId();
			Long plantIdQ3 = models.get(0).getPlantId();
			Long modelYearQ3 = models.get(0).getModelYear();
			Long divisionIdQ3 = models.get(0).getDivisionId();
			String code = models.get(0).getCode();
			
			
			String mdlType = "";			
			if(modelTypeIdQ3.getModelTypeId() == 1) {
				mdlType = "KC";
			}
			
			if(modelTypeIdQ3.getModelTypeId() == 2) {
				mdlType = "KA";
			}

			AfeModelEntity modelQ3 = models.get(0);

			// QUERY4

			Optional<AfeDivisionEntity> afeDivision = afeDivisionRepository.findById(divisionIdQ3);
			if (!afeDivision.isPresent()) {

				genstaMessagesHandler.createAndLogMessageDvisionNoExist(divisionIdQ3, fixedOrderId,
						"SELECT * FROM AFE_DIVISION WHERE ID");
				break;
			}

			String abbrevationQ4 = afeDivision.get().getAbbreviation();

			// QUERY5
			Optional<AfePlantEntity> afePlant = afePlantRepository.findById(plantIdQ3);
			if (!afePlant.isPresent()) {

				genstaMessagesHandler.createAndLogMessagePlantNoExist(divisionIdQ3, fixedOrderId,
						"SELECT * FROM AFE_DIVISION WHERE ID");
				break;
			}

			String abbrevationQ5 = afePlant.get().getAbbreviation();

			// QUERY6
			List<AfeColorEntity> colors = afeColorRepository.findAllById(colorIdQ2);

			if (colors.isEmpty()) {

				genstaMessagesHandler.createAndLogMessageColorNoExists(colorIdQ2, fixedOrderId,
						"SELEC * FROM AFE_COLOR WHERE ID");

				break;

			}

			AfeColorEntity colorQ6 = colors.get(0);

			String colorCodeQ6 = colorQ6.getCode();
			String colorExtCodeQ6 = colorQ6.getExteriorCode();
			String colorIntCodeQ6 = colorQ6.getInteriorCode();

			AfeColorEntity colorQ5 = colors.get(0);

			// QUERY7
			List<AfeEventStatusEntity> eventStatus = afeEventStatusRepository.findAllByFixedOrder(Q1fixedOrderId);

			if (eventStatus.isEmpty()) {
				genstaMessagesHandler.createAndLogMessageEventStatusNoExists(Q1fixedOrderId,
						"SELECT o FROM AfeEventStatusEntity o WHERE o.fixedOrderId = :fixedOrderId ");

				log.debug("ProcessFile:: FixedOrder DOESN'T exist in EVENT_STATUS");

				break;

			}
			
			Long estdDelvryDtQ7 = eventStatus.get(0);
			Date estdDelvryDtQ7;

			// QUERY8
			List<AfeEventStatusHistoryEntity> statusHistory = afeEventStatusHistoryRepository
					.findAllByCode(divisionIdQ3);

			if (statusHistory.isEmpty()) {
				genstaMessagesHandler.createAndLogMessageEventStatusHistoryNoExists(Q1fixedOrderId,
						"SELECT o FROM AfeEventStatusEntity o WHERE o.fixedOrderId = :fixedOrderId ");

				// return to main line process loop
				break;
			}

			AfeEventStatusHistoryEntity actionQ6 = statusHistory.get(0);

			// QUERY9
			List<EventCodeEntity> eventCodes = afeEventCodeRepository.findAllByEventCode(eventCodeIdQ11);

			if (eventCodes.isEmpty()) {
				genstaMessagesHandler.createAndLogMessageEventCodeNoExists(Q1fixedOrderId,
						"SELECT o FROM AfeEventStatusEntity o WHERE o.fixedOrderId = :fixedOrderId ");

				break;

			}

			Long eventCodeNumberQ9 = eventCodes.getEventCodeNumber();
			Long descriptionQ9 = eventCodes.getDescription();
			
			try {

				fileLine.append(completeSpaces(abbrevationQ4.trim(), "XPROD-DIV-CD"));
				fileLine.append(completeSpaces(abbrevationQ5.trim(), "PLANT-ID"));
				fileLine.append(completeSpaces(Q1weekStartDay.toString(), "GM-PROD-WEEK-START-DAY"));
				fileLine.append(completeSpaces(Q1ordDueDate.toString(), "GM-ORD-DUE-DT"));
				fileLine.append(completeSpaces(code, "MDL-ID"));
				fileLine.append(completeSpaces(mdlType, "MDL-TYP-CD"));
				fileLine.append("   "); //"MDL-OPT-PKG-CD"
				
				fileLine.append(completeSpaces(colorCodeQ6.trim(), "MDL-MFG-COLOR-ID"));
				fileLine.append(completeSpaces(colorExtCodeQ6.trim(), "EXTR-COLOR-CD"));
				fileLine.append(completeSpaces(colorIntCodeQ6.trim(), "INT-COLOR-CD"));
				
				fileLine.append(completeSpaces(q1OrderNumber.trim(), "GM-ORD-STA-VEH-ORD-NO"));
				
				fileLine.append(completeSpaces(externConfigIdQ1, "GM-ORD-STA-EXTERN-CONFIG-ID"));
				
				fileLine.append(completeSpaces(vinNumberQ1, "GM-ORD-STA-VIN-NO"));

				String timeStamp = GenstaafeUtils.formatDateTimeStamp(estdDelvryDtQ7);
				fileLine.append(completeSpaces(timeStamp, "GM-ORD-STA-VO-LAST-CHG-TMSTP"));
				
				fileLine.append(completeSpaces(orderTypeQ1.trim(), "GM-ORD-STA-TYP-CD"));
				fileLine.append(completeSpaces(timeStamp, "GM-ORD-STA-TARGET-PRODN-DT"));
				fileLine.append(completeSpaces(timeStamp, "GM-ORD-STA-ESTD-DELVRY-DT"));
				

				fileLine.append(completeSpaces(eventCodeNumberQ9, "GM-ORD-STA-CURR-VEH-EVNT-CD"));
				fileLine.append(completeSpaces(eventCodeNumberQ9, "GM-ORD-STA-CURR_EVENT-STAT-DT"));
				fileLine.append(completeSpaces(eventCodeNumberQ9, "GM-ORD-STAK-DLR-REC-BUS-AST-DT"));

				fileLine.append(completeSpaces(descriptionQ9, "GM-ORD-ACK-CURR-VEH-EVNT-DESC"));

				
				log.info("Length: " + fileLine.length());
				log.info(fileLine.toString());

				fileLine = completeLineSpaces(fileLine, templateControl);

				GenstaafeUtils.checkFileIfWriteFile(folderSource, fileName, fileLine.toString());
			} catch (GenstaafeException e) {
				log.info("El archivo " + fileName + " NO fue creado correctamente en la ubicación: " + folderSource);
				event = new EventVO(serviceName, GenstaafeConstants.ZERO_STATUS,
						"El archivo " + fileName + " NO fue creado correctamente en la ubicación: " + folderSource, "");
				logEventService.sendLogEvent(event);
				break;
			}

			success = Boolean.TRUE;
			fileLine.setLength(0);

			movFileService.sendMoveFileMessage(new MoveFileVO(1L, successMessage, fileName));

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

}
