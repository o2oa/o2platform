package com.x.cms.assemble.control.jaxrs.document;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import com.x.base.core.project.config.Token;
import com.x.base.core.project.exception.ExceptionAccessDenied;
import com.x.base.core.project.tools.DateTools;
import com.x.cms.assemble.control.Business;
import com.x.cms.core.entity.enums.DocumentStatus;
import com.x.cms.core.entity.query.DocumentNotify;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.annotation.CheckPersistType;
import com.x.base.core.project.annotation.AuditLog;
import com.x.base.core.project.annotation.FieldDescribe;
import com.x.base.core.project.bean.WrapCopier;
import com.x.base.core.project.bean.WrapCopierFactory;
import com.x.base.core.project.cache.CacheManager;
import com.x.base.core.project.config.Config;
import com.x.base.core.project.config.StorageMapping;
import com.x.base.core.project.exception.ExceptionWhen;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.WoId;
import com.x.base.core.project.logger.Logger;
import com.x.base.core.project.logger.LoggerFactory;
import com.x.cms.assemble.control.ThisApplication;
import com.x.cms.assemble.control.jaxrs.permission.element.PermissionInfo;
import com.x.cms.core.entity.AppInfo;
import com.x.cms.core.entity.CategoryInfo;
import com.x.cms.core.entity.Document;
import com.x.cms.core.entity.FileInfo;
import com.x.cms.core.entity.element.Form;
import com.x.processplatform.core.entity.content.Attachment;

/**
 * 直接发布文档内容
 *
 * @author O2LEE
 *
 */
public class ActionPersistPublishContent extends BaseAction {

	private static Logger logger = LoggerFactory.getLogger(ActionPersistPublishContent.class);

	protected ActionResult<Wo> execute(HttpServletRequest request, JsonElement jsonElement,
			EffectivePerson effectivePerson) throws Exception {
		ActionResult<Wo> result = new ActionResult<>();
		Document document = null;
		Wi wi = this.convertToWrapIn(jsonElement, Wi.class);
		Boolean check = true;

		if (StringUtils.isEmpty(wi.getCategoryId())) {
			throw new ExceptionDocumentCategoryIdEmpty();
		}

		CategoryInfo categoryInfo;
		AppInfo appInfo = null;

		Document oldDocument = documentQueryService.get(wi.getId());
		if (oldDocument != null) {
			categoryInfo = categoryInfoServiceAdv.get(oldDocument.getCategoryId());
			appInfo = appInfoServiceAdv.get(oldDocument.getAppId());
		} else {
			categoryInfo = categoryInfoServiceAdv.get(wi.getCategoryId());
			if (categoryInfo != null) {
				appInfo = appInfoServiceAdv.get(categoryInfo.getAppId());
			}
		}

		if (categoryInfo == null) {
			throw new ExceptionCategoryInfoNotExists(wi.getCategoryId());
		}
		if (appInfo == null) {
			throw new ExceptionAppInfoNotExists(categoryInfo.getAppId());
		}

		Business business = new Business(null);
		if (!business.isDocumentEditor(effectivePerson, appInfo, categoryInfo, oldDocument)) {
			throw new ExceptionAccessDenied(effectivePerson);
		}

		// 查询分类设置的编辑表单
		if (StringUtils.isEmpty(categoryInfo.getFormId())) {
			throw new ExceptionCategoryFormIdEmpty();
		}

		Form form = formServiceAdv.get(categoryInfo.getFormId());
		if (form == null) {
			throw new ExceptionFormForEditNotExists(categoryInfo.getFormId());
		} else {
			wi.setForm(form.getId());
			wi.setFormName(form.getName());
		}

		if (check) {
			if (categoryInfo.getReadFormId() != null && !categoryInfo.getReadFormId().isEmpty()) {
				try {
					form = formServiceAdv.get(categoryInfo.getReadFormId());
					if (form == null) {
						check = false;
						Exception exception = new ExceptionFormForReadNotExists(categoryInfo.getReadFormId());
						result.error(exception);
					} else {
						wi.setReadFormId(form.getId());
						wi.setReadFormName(form.getName());
					}
				} catch (Exception e) {
					check = false;
					Exception exception = new ExceptionDocumentInfoProcess(e,
							"系统在根据ID查询阅读表单时发生异常！ID：" + categoryInfo.getReadFormId());
					result.error(exception);
					logger.error(e, effectivePerson, request, null);
				}
			}
		}

		if (check) {
			wi.setAppId(categoryInfo.getAppId());
			wi.setAppName(appInfo.getAppName());
			wi.setAppAlias(appInfo.getAppAlias());
			wi.setCategoryName(categoryInfo.getCategoryName());
			wi.setCategoryId(categoryInfo.getId());
			wi.setCategoryAlias(categoryInfo.getCategoryAlias());
			if (!Document.DOCUMENT_TYPE_INFO.equals(wi.getDocumentType()) && !Document.DOCUMENT_TYPE_DATA.equals(wi.getDocumentType())) {
				wi.setDocumentType(StringUtils.isBlank(categoryInfo.getDocumentType()) ? Document.DOCUMENT_TYPE_INFO : categoryInfo.getDocumentType());
			}
			if (wi.getPictureList() != null && !wi.getPictureList().isEmpty()) {
				wi.setHasIndexPic(true);
			}
		}

		if (check) {
			try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {

				if (StringUtils.isNotEmpty(wi.getIdentity())) {
					wi.setCreatorIdentity(wi.getIdentity());
					wi.setCreatorPerson(userManagerService.getPersonNameWithIdentity(wi.getIdentity()));
				}

				if (StringUtils.isEmpty(wi.getCreatorPerson())) {
					wi.setCreatorPerson(effectivePerson.getDistinguishedName());
				}

				if (StringUtils.isEmpty(wi.getCreatorIdentity())) {
					if (StringUtils.equals(EffectivePerson.CIPHER, effectivePerson.getDistinguishedName())
							|| StringUtils.equals(Token.defaultInitialManager,
									effectivePerson.getDistinguishedName())) {
						wi.setCreatorIdentity(effectivePerson.getDistinguishedName());
						wi.setCreatorPerson(effectivePerson.getDistinguishedName());
						wi.setCreatorUnitName(effectivePerson.getDistinguishedName());
						wi.setCreatorTopUnitName(effectivePerson.getDistinguishedName());
					} else {
						// 尝试一下根据传入的用户或者当前用户获取用户的第一个身份
						wi.setCreatorIdentity(userManagerService.getMajorIdentityWithPerson(wi.getCreatorPerson()));
					}
				}

				if (!StringUtils.equals(EffectivePerson.CIPHER, wi.getCreatorIdentity())
						&& !StringUtils.equals(Token.defaultInitialManager, wi.getCreatorIdentity())) {
					// 说明是实际的用户，并不使用cipher和xadmin代替
					if (StringUtils.isNotEmpty(wi.getCreatorIdentity())) {
						wi.setCreatorPerson(userManagerService.getPersonNameWithIdentity(wi.getCreatorIdentity()));
						wi.setCreatorUnitName(userManagerService.getUnitNameByIdentity(wi.getCreatorIdentity()));
						wi.setCreatorTopUnitName(userManagerService.getTopUnitNameByIdentity(wi.getCreatorIdentity()));
					} else {
						Exception exception = new ExceptionPersonHasNoIdentity(wi.getCreatorIdentity());
						result.error(exception);
					}
				}
			} catch (Throwable th) {
				th.printStackTrace();
				result.error(th);
			}
		}

		if (check) {
			if (StringUtils.isEmpty(wi.getTitle())) {
				wi.setTitle(appInfo.getAppName() + " - " + categoryInfo.getCategoryName() + " - 无标题文档");
			}
		}

		if (check) {
			try {
				if (!DocumentStatus.WAIT_PUBLISH.getValue().equals(wi.getDocStatus())) {
					wi.setDocStatus(DocumentStatus.PUBLISHED.getValue());
				}
				if (wi.getPublishTime() == null) {
					wi.setPublishTime(new Date());
				}
				document = Wi.copier.copy(wi);
				document.getProperties().setDocumentNotify(wi.getDocumentNotify());
				document.getProperties().setCloudPictures(wi.getCloudPictures());
				document.setId(wi.getId());
				document.setPpFormId(wi.getWf_formId());
				document = documentPersistService.save(document, wi.getDocData(), categoryInfo.getProjection());
			} catch (Exception e) {
				check = false;
				Exception exception = new ExceptionDocumentInfoProcess(e, "系统在创建文档信息时发生异常！");
				result.error(exception);
				logger.error(e, effectivePerson, request, null);
			}
		}

		// 从流程管理中复制所有的附件到CMS
		if (check) {
			if (wi.getWf_attachmentIds() != null && wi.getWf_attachmentIds().length > 0) {
				FileInfo fileInfo = null;
				Attachment attachment = null;
				StorageMapping mapping_attachment = null;
				StorageMapping mapping_fileInfo = null;
				InputStream input = null;
				byte[] attachment_content = null;
				for (String attachmentId : wi.getWf_attachmentIds()) {
					try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
						document = emc.find(document.getId(), Document.class, ExceptionWhen.not_found);
						attachment = emc.find(attachmentId, Attachment.class, ExceptionWhen.not_found);
						if (attachment != null) {
							emc.beginTransaction(FileInfo.class);
							emc.beginTransaction(Document.class);

							mapping_attachment = ThisApplication.context().storageMappings().get(Attachment.class,
									attachment.getStorage());
							attachment_content = attachment.readContent(mapping_attachment);

							mapping_fileInfo = ThisApplication.context().storageMappings().random(FileInfo.class);
							fileInfo = concreteFileInfo(effectivePerson.getDistinguishedName(), document,
									mapping_fileInfo, attachment.getName(), attachment.getSite());
							input = new ByteArrayInputStream(attachment_content);
							fileInfo.saveContent(mapping_fileInfo, input, attachment.getName(),
									Config.general().getStorageEncrypt());
							if (attachment.getOrderNumber() != null) {
								fileInfo.setSeqNumber(attachment.getOrderNumber());
							}
							fileInfo.setName(attachment.getName());
							emc.check(document, CheckPersistType.all);
							emc.persist(fileInfo, CheckPersistType.all);

							emc.commit();
						}
					} catch (Throwable th) {
						th.printStackTrace();
						result.error(th);
					} finally {
						if (input != null) {
							input.close();
						}
					}
				}
			}
		}

		// 从CMS其他文档中复制所有的附件到CMS
		if (check) {
			if (wi.getCms_attachmentIds() != null && wi.getCms_attachmentIds().length > 0) {
				FileInfo fileInfo = null;
				FileInfo copyFileInfo = null;
				StorageMapping mapping_attachment = null;
				StorageMapping mapping_fileInfo = null;
				byte[] attachment_content = null;
				for (String attachmentId : wi.getCms_attachmentIds()) {
					try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
						document = emc.find(document.getId(), Document.class, ExceptionWhen.not_found);
						copyFileInfo = emc.find(attachmentId, FileInfo.class, ExceptionWhen.not_found);
						if (copyFileInfo != null) {
							emc.beginTransaction(FileInfo.class);
							emc.beginTransaction(Document.class);

							mapping_attachment = ThisApplication.context().storageMappings().get(FileInfo.class,
									copyFileInfo.getStorage());
							attachment_content = copyFileInfo.readContent(mapping_attachment);

							mapping_fileInfo = ThisApplication.context().storageMappings().random(FileInfo.class);
							fileInfo = concreteFileInfo(effectivePerson.getDistinguishedName(), document,
									mapping_fileInfo, copyFileInfo.getName(), copyFileInfo.getSite());
							fileInfo.saveContent(mapping_fileInfo, attachment_content, copyFileInfo.getName(),
									Config.general().getStorageEncrypt());
							fileInfo.setName(copyFileInfo.getName());
							emc.check(document, CheckPersistType.all);
							emc.persist(fileInfo, CheckPersistType.all);

							emc.commit();
						}
					} catch (Throwable th) {
						th.printStackTrace();
						result.error(th);
					}
				}
			}
		}

		Wo wo = new Wo();
		wo.setId(document.getId());
		result.setData(wo);

		if (check) {
			try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
				logService.log(emc, wi.getCreatorIdentity(), document.getCategoryAlias() + ":" + document.getTitle(),
						document.getAppId(), document.getCategoryId(), document.getId(), "", "DOCUMENT", "发布");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (check && !wi.getSkipPermission()) {
			// 将读者以及作者信息持久化到数据库中
			try {
				if (oldDocument == null || wi.getReaderList() != null || wi.getAuthorList() != null) {
					documentPersistService.refreshDocumentPermission(document.getId(), wi.getReaderList(),
							wi.getAuthorList());
				}
			} catch (Exception e) {
				Exception exception = new ExceptionDocumentInfoProcess(e, "系统在核对文档访问管理权限信息时发生异常！");
				result.error(exception);
				logger.error(e, effectivePerson, request, null);
			}
		}

		CacheManager.notify(FileInfo.class);
		CacheManager.notify(Document.class);
		return result;
	}

	private FileInfo concreteFileInfo(String person, Document document, StorageMapping storage, String name,
			String site) throws Exception {
		String fileName = UUID.randomUUID().toString();
		String extension = FilenameUtils.getExtension(name);
		FileInfo attachment = new FileInfo();
		if (StringUtils.isEmpty(extension)) {
			throw new Exception("file extension is empty.");
		} else {
			fileName = fileName + "." + extension;
		}
		if (name.indexOf("\\") > 0) {
			name = StringUtils.substringAfterLast(name, "\\");
		}
		if (name.indexOf("/") > 0) {
			name = StringUtils.substringAfterLast(name, "/");
		}
		attachment.setCreateTime(new Date());
		attachment.setLastUpdateTime(new Date());
		attachment.setExtension(extension);
		attachment.setName(name);
		attachment.setFileName(fileName);
		attachment.setStorage(storage.getName());
		attachment.setAppId(document.getAppId());
		attachment.setCategoryId(document.getCategoryId());
		attachment.setDocumentId(document.getId());
		attachment.setCreatorUid(person);
		attachment.setSite(site);
		attachment.setFileHost("");
		attachment.setFilePath("");
		attachment.setFileType("ATTACHMENT");

		return attachment;
	}

	public static class Wi {

		public static final WrapCopier<Wi, Document> copier = WrapCopierFactory.wi(Wi.class, Document.class, null,
				null);

		private String id = null;

		@FieldDescribe("文档操作者身份")
		private String identity = null;

		@FieldDescribe("启动流程的JobId.")
		private String wf_jobId = null;

		@FieldDescribe("启动流程的WorkId.")
		private String wf_workId = null;

		@FieldDescribe("流程的表单Id.")
		private String wf_formId = null;

		@FieldDescribe("启动流程的附件列表.")
		private String[] wf_attachmentIds = null;

		@FieldDescribe("内容管理其他文档的附件列表，非必填")
		private String[] cms_attachmentIds = null;

		@FieldDescribe("文档数据JSON对象.")
		private JsonElement docData = null;

		@FieldDescribe("文档读者，Json数组，权限对象需要包含四个属性:<br/>permission权限类别：读者|阅读|作者|管理,  <br/>permissionObjectType使用者类别：所有人|组织|人员|群组, <br/>permissionObjectCode使用者编码：所有人|组织编码|人员UID|群组编码, <br/>permissionObjectName使用者名称：所有人|组织名称|人员名称|群组名称")
		private List<PermissionInfo> readerList = null;

		@FieldDescribe("文档编辑者, ，Json数组，权限对象需要包含四个属性:<br/>permission权限类别：读者|阅读|作者|管理,  <br/>permissionObjectType使用者类别：所有人|组织|人员|群组, <br/>permissionObjectCode使用者编码：所有人|组织编码|人员UID|群组编码, <br/>permissionObjectName使用者名称：所有人|组织名称|人员名称|群组名称")
		private List<PermissionInfo> authorList = null;

		private List<String> cloudPictures = null;

		@FieldDescribe("不修改权限（跳过权限设置，保留原来的设置）， True|False.")
		private Boolean skipPermission = false;

		@FieldDescribe("文档摘要，70字以内")
		private String summary;

		@FieldDescribe("文档标题，70字以内")
		private String title;

		@FieldDescribe("客体密级标识")
		private Integer objectSecurityClearance;

		@FieldDescribe("文档类型，跟随分类类型，信息（默认） | 数据")
		private String documentType = "信息";

		@FieldDescribe("文档状态: published | waitPublish")
		private String docStatus = "published";

		@FieldDescribe("分类ID")
		private String categoryId;

		private String appId;

		private String appName;

		private String appAlias;

		private String categoryName;

		private String categoryAlias;

		private String form;

		private String formName;

		private String importBatchName;

		private String readFormId;

		private String readFormName;

		private String creatorPerson;

		private String creatorIdentity;

		private String creatorUnitName;

		private String creatorTopUnitName;

		private String description = null;

		private Long viewCount = 0L;

		private Long commendCount = 0L;

		private Long commentCount = 0L;

		@FieldDescribe("发布时间，当状态为waitPublish时需指定发布时间")
		private Date publishTime;

		private Date modifyTime;

		@FieldDescribe("是否置顶")
		private Boolean isTop = false;

		private Boolean isNotice = true;

		private Boolean hasIndexPic = false;

		private Boolean reviewed = false;

		private String sequenceTitle = "";

		private String sequenceAppAlias = "";

		private String sequenceCategoryAlias = "";

		private String sequenceCreatorPerson = "";

		private String sequenceCreatorUnitName = "";

		private List<String> readPersonList;

		private List<String> readUnitList;

		private List<String> readGroupList;

		private List<String> authorPersonList;

		private List<String> authorUnitList;

		private List<String> authorGroupList;

		private List<String> managerList;

		private List<String> pictureList;

		@FieldDescribe("定时发布文档消息提醒对象(参数同消息发布接口对象).")
		private DocumentNotify documentNotify;

		@FieldDescribe("业务数据String值01.")
		private String stringValue01;

		@FieldDescribe("业务数据String值02.")
		private String stringValue02;

		@FieldDescribe("业务数据String值03.")
		private String stringValue03;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Boolean getTop() {
			return isTop;
		}

		public void setTop(Boolean top) {
			isTop = top;
		}

		public String getSummary() {
			return summary;
		}

		public void setSummary(String summary) {
			this.summary = summary;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getDocumentType() {
			return documentType;
		}

		public void setDocumentType(String documentType) {
			this.documentType = documentType;
		}

		public String getAppId() {
			return appId;
		}

		public void setAppId(String appId) {
			this.appId = appId;
		}

		public String getAppName() {
			return appName;
		}

		public void setAppName(String appName) {
			this.appName = appName;
		}

		public String getAppAlias() {
			return appAlias;
		}

		public void setAppAlias(String appAlias) {
			this.appAlias = appAlias;
		}

		public String getCategoryId() {
			return categoryId;
		}

		public void setCategoryId(String categoryId) {
			this.categoryId = categoryId;
		}

		public String getCategoryName() {
			return categoryName;
		}

		public void setCategoryName(String categoryName) {
			this.categoryName = categoryName;
		}

		public String getCategoryAlias() {
			return categoryAlias;
		}

		public void setCategoryAlias(String categoryAlias) {
			this.categoryAlias = categoryAlias;
		}

		public String getForm() {
			return form;
		}

		public void setForm(String form) {
			this.form = form;
		}

		public String getFormName() {
			return formName;
		}

		public void setFormName(String formName) {
			this.formName = formName;
		}

		public String getImportBatchName() {
			return importBatchName;
		}

		public void setImportBatchName(String importBatchName) {
			this.importBatchName = importBatchName;
		}

		public String getReadFormId() {
			return readFormId;
		}

		public void setReadFormId(String readFormId) {
			this.readFormId = readFormId;
		}

		public String getReadFormName() {
			return readFormName;
		}

		public void setReadFormName(String readFormName) {
			this.readFormName = readFormName;
		}

		public String getCreatorPerson() {
			return creatorPerson;
		}

		public void setCreatorPerson(String creatorPerson) {
			this.creatorPerson = creatorPerson;
		}

		public String getCreatorIdentity() {
			return creatorIdentity;
		}

		public void setCreatorIdentity(String creatorIdentity) {
			this.creatorIdentity = creatorIdentity;
		}

		public String getCreatorUnitName() {
			return creatorUnitName;
		}

		public void setCreatorUnitName(String creatorUnitName) {
			this.creatorUnitName = creatorUnitName;
		}

		public String getCreatorTopUnitName() {
			return creatorTopUnitName;
		}

		public void setCreatorTopUnitName(String creatorTopUnitName) {
			this.creatorTopUnitName = creatorTopUnitName;
		}

		public String getDocStatus() {
			return docStatus;
		}

		public void setDocStatus(String docStatus) {
			this.docStatus = docStatus;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public Long getViewCount() {
			return viewCount;
		}

		public void setViewCount(Long viewCount) {
			this.viewCount = viewCount;
		}

		public Long getCommendCount() {
			return commendCount;
		}

		public void setCommendCount(Long commendCount) {
			this.commendCount = commendCount;
		}

		public Long getCommentCount() {
			return commentCount;
		}

		public void setCommentCount(Long commentCount) {
			this.commentCount = commentCount;
		}

		public Date getPublishTime() {
			return publishTime;
		}

		public void setPublishTime(Date publishTime) {
			this.publishTime = publishTime;
		}

		public Date getModifyTime() {
			return modifyTime;
		}

		public void setModifyTime(Date modifyTime) {
			this.modifyTime = modifyTime;
		}

		public Boolean getIsTop() {
			return isTop;
		}

		public void setIsTop(Boolean isTop) {
			this.isTop = isTop;
		}

		public Boolean getHasIndexPic() {
			return hasIndexPic;
		}

		public void setHasIndexPic(Boolean hasIndexPic) {
			this.hasIndexPic = hasIndexPic;
		}

		public Boolean getReviewed() {
			return reviewed;
		}

		public void setReviewed(Boolean reviewed) {
			this.reviewed = reviewed;
		}

		public String getSequenceTitle() {
			return sequenceTitle;
		}

		public void setSequenceTitle(String sequenceTitle) {
			this.sequenceTitle = sequenceTitle;
		}

		public String getSequenceAppAlias() {
			return sequenceAppAlias;
		}

		public void setSequenceAppAlias(String sequenceAppAlias) {
			this.sequenceAppAlias = sequenceAppAlias;
		}

		public String getSequenceCategoryAlias() {
			return sequenceCategoryAlias;
		}

		public void setSequenceCategoryAlias(String sequenceCategoryAlias) {
			this.sequenceCategoryAlias = sequenceCategoryAlias;
		}

		public String getSequenceCreatorPerson() {
			return sequenceCreatorPerson;
		}

		public void setSequenceCreatorPerson(String sequenceCreatorPerson) {
			this.sequenceCreatorPerson = sequenceCreatorPerson;
		}

		public String getSequenceCreatorUnitName() {
			return sequenceCreatorUnitName;
		}

		public void setSequenceCreatorUnitName(String sequenceCreatorUnitName) {
			this.sequenceCreatorUnitName = sequenceCreatorUnitName;
		}

		public List<String> getReadPersonList() {
			return readPersonList;
		}

		public void setReadPersonList(List<String> readPersonList) {
			this.readPersonList = readPersonList;
		}

		public List<String> getReadUnitList() {
			return readUnitList;
		}

		public void setReadUnitList(List<String> readUnitList) {
			this.readUnitList = readUnitList;
		}

		public List<String> getReadGroupList() {
			return readGroupList;
		}

		public void setReadGroupList(List<String> readGroupList) {
			this.readGroupList = readGroupList;
		}

		public List<String> getAuthorPersonList() {
			return authorPersonList;
		}

		public void setAuthorPersonList(List<String> authorPersonList) {
			this.authorPersonList = authorPersonList;
		}

		public List<String> getAuthorUnitList() {
			return authorUnitList;
		}

		public void setAuthorUnitList(List<String> authorUnitList) {
			this.authorUnitList = authorUnitList;
		}

		public List<String> getAuthorGroupList() {
			return authorGroupList;
		}

		public void setAuthorGroupList(List<String> authorGroupList) {
			this.authorGroupList = authorGroupList;
		}

		public List<String> getManagerList() {
			return managerList;
		}

		public void setManagerList(List<String> managerList) {
			this.managerList = managerList;
		}

		public List<String> getPictureList() {
			return pictureList;
		}

		public void setPictureList(List<String> pictureList) {
			this.pictureList = pictureList;
		}

		public Boolean getSkipPermission() {
			return skipPermission;
		}

		public void setSkipPermission(Boolean skipPermission) {
			this.skipPermission = skipPermission;
		}

		public String getIdentity() {
			return identity;
		}

		public void setIdentity(String identity) {
			this.identity = identity;
		}

		public List<PermissionInfo> getReaderList() {
			return readerList;
		}

		public void setReaderList(List<PermissionInfo> readerList) {
			this.readerList = readerList;
		}

		public JsonElement getDocData() {
			return docData;
		}

		public void setDocData(JsonElement docData) {
			this.docData = docData;
		}

		public String getWf_jobId() {
			return wf_jobId;
		}

		public String getWf_workId() {
			return wf_workId;
		}

		public String[] getWf_attachmentIds() {
			return wf_attachmentIds;
		}

		public void setWf_jobId(String wf_jobId) {
			this.wf_jobId = wf_jobId;
		}

		public void setWf_workId(String wf_workId) {
			this.wf_workId = wf_workId;
		}

		public void setWf_attachmentIds(String[] wf_attachmentIds) {
			this.wf_attachmentIds = wf_attachmentIds;
		}

		public List<String> getCloudPictures() {
			return cloudPictures;
		}

		public void setCloudPictures(List<String> cloudPictures) {
			this.cloudPictures = cloudPictures;
		}

		public List<PermissionInfo> getAuthorList() {
			return authorList;
		}

		public void setAuthorList(List<PermissionInfo> authorList) {
			this.authorList = authorList;
		}

		public String[] getCms_attachmentIds() {
			return cms_attachmentIds;
		}

		public void setCms_attachmentIds(String[] cms_attachmentIds) {
			this.cms_attachmentIds = cms_attachmentIds;
		}

		public Boolean getNotice() {
			return isNotice;
		}

		public void setNotice(Boolean notice) {
			isNotice = notice;
		}

		public String getStringValue01() {
			return stringValue01;
		}

		public void setStringValue01(String stringValue01) {
			this.stringValue01 = stringValue01;
		}

		public String getStringValue02() {
			return stringValue02;
		}

		public void setStringValue02(String stringValue02) {
			this.stringValue02 = stringValue02;
		}

		public String getStringValue03() {
			return stringValue03;
		}

		public void setStringValue03(String stringValue03) {
			this.stringValue03 = stringValue03;
		}

		public String getWf_formId() {
			return wf_formId;
		}

		public void setWf_formId(String wf_formId) {
			this.wf_formId = wf_formId;
		}

		public DocumentNotify getDocumentNotify() {
			return documentNotify;
		}

		public void setDocumentNotify(DocumentNotify documentNotify) {
			this.documentNotify = documentNotify;
		}

		public Integer getObjectSecurityClearance() {
			return objectSecurityClearance;
		}

		public void setObjectSecurityClearance(Integer objectSecurityClearance) {
			this.objectSecurityClearance = objectSecurityClearance;
		}

	}

	public static class Wo extends WoId {

	}
}
