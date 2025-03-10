MWF.xApplication.process.FormDesigner.Module = MWF.xApplication.process.FormDesigner.Module || {};
MWF.xDesktop.requireApp("process.FormDesigner", "Module.$Container", null, false);
MWF.xApplication.process.FormDesigner.Module.Datatable$Title = MWF.FCDatatable$Title = new Class({
	Extends: MWF.FC$Element,
	Implements: [Options, Events],
	options: {
		"style": "default",
		"propertyPath": "../x_component_process_FormDesigner/Module/Datatable$Title/datatable$Title.html",
		"actions": [
		    {
		    	"name": "insertCol",
		    	"icon": "insertCol1.png",
		    	"event": "click",
		    	"action": "insertCol",
		    	"title": MWF.LP.process.formAction.insertCol
		    },
		    {
		    	"name": "deleteCol",
		    	"icon": "deleteCol1.png",
		    	"event": "click",
		    	"action": "deleteCol",
		    	"title": MWF.LP.process.formAction.deleteCol
		    },
			{
				"name": "selectParent",
				"icon": "selectParent.png",
				"event": "click",
				"action": "selectParent",
				"title": MWF.APPFD.LP.formAction["selectParent"]
			}
		]
	},
	
	initialize: function(form, options){
		this.setOptions(options);
		
		this.path = "../x_component_process_FormDesigner/Module/Datatable$Title/";
		this.cssPath = "../x_component_process_FormDesigner/Module/Datatable$Title/"+this.options.style+"/css.wcss";

		this._loadCss();
		this.moduleType = "element";
		this.moduleName = "datatable$Title";
		
		this.Node = null;
		this.form = form;
	},
	_setNodeProperty: function(){
		if (this.form.moduleList.indexOf(this)==-1) this.form.moduleList.push(this);
		if (this.form.moduleNodeList.indexOf(this.node)==-1) this.form.moduleNodeList.push(this.node);
		if (this.form.moduleElementNodeList.indexOf(this.node)==-1) this.form.moduleElementNodeList.push(this.node);
		this.node.store("module", this);
		this.setPrefixOrSuffix();
	},
    setAllStyles: function(){
        Object.each(this.json.styles, function(value, key){
            var reg = /^border\w*/ig;
            if (!key.test(reg)){
                if (key) this.node.setStyle(key, value);
            }
        }.bind(this));
        this.setPropertiesOrStyles("properties");
        this.reloadMaplist();
    },
	_dragIn: function(module){
		this.parentContainer._dragIn(module);
	},
	
	over: function(){
		if (this.form.selectedModules.indexOf(this)==-1){
			if (!this.form.moveModule) if (this.form.currentSelectedModule!=this) this.node.setStyles({
				"border-width": "1px",
				"border-color": "#0072ff"
			});
		}
	},
	unOver: function(){
		if (this.form.selectedModules.indexOf(this)==-1){
			if (!this.form.moveModule) if (this.form.currentSelectedModule!=this) this.node.setStyles({
				"border-width": "1px",
				"border-color": "#999"
			});
		}
	},
	unSelected: function(){
		this.node.setStyles({
			"border-width": "1px",
			"border-color": "#999"
		});
		this._hideActions();
		this.form.currentSelectedModule = null;
		
		this.hideProperty();
	},
	
	load : function(json, node, parent){
		this.json = json;
		this.node= node;
		this.node.store("module", this);
		this.node.setStyles(this.css.moduleNode);
		this.node.set("text", this.json.name || "DataTitle");
		
		if (!this.json.id){
			var id = this._getNewId(parent.json.id);
			this.json.id = id;
		}
		
		node.set({
			"MWFType": "datatable$Title",
			"id": this.json.id
		});
		
		if (!this.form.json.moduleList[this.json.id]){
			this.form.json.moduleList[this.json.id] = this.json;
		}
		this._initModule();
		this._loadTreeNode(parent);
		
		this.parentContainer = this.treeNode.parentNode.module;
        this._setEditStyle_custom("id");
        this.json.moduleName = this.moduleName;

		if( this.json.isShow === false ){
			this._switchShow();
		}
	},
	
	_createMoveNode: function(){
		return false;
	},
	// _setEditStyle_custom: function(name){
	//
	// },
	_dragInLikeElement: function(module){
		return false;
	},

	setCustomStyles: function(){
		this._recoveryModuleData();

		var border = this.node.getStyle("border");
		this.node.clearStyles();
		this.node.setStyles(this.css.moduleNode);

		if (this.initialStyles) this.node.setStyles(this.initialStyles);
		this.node.setStyle("border", border);

		Object.each(this.json.styles, function(value, key){
			var reg = /^border\w*/ig;
			if (!key.test(reg)){
				this.node.setStyle(key, value);
			}
		}.bind(this));

		this.setCustomNodeStyles(this.node, this.parentContainer.json.titleStyles);

		if( this.json.isShow === false ){
			this._switchShow();
		}
	},

	insertCol: function(){
		var module = this;
		var url = this.path+"insertCol.html";
		MWF.require("MWF.widget.Dialog", function(){
			var size = $(document.body).getSize();			
			var x = size.x/2-150;
			var y = size.y/2-90;

			var dlg = new MWF.DL({
				"title": "Insert Col",
				"style": "property",
				"top": y,
				"left": x-40,
				"fromTop":size.y/2-45,
				"fromLeft": size.x/2,
				"width": 300,
				"height": 180,
				"url": url,
				"lp": MWF.xApplication.process.FormDesigner.LP.propertyTemplate,
				"buttonList": [
				    {
				    	"text": MWF.APPFD.LP.button.ok,
				    	"action": function(){

				    		module._insertCol();
				    		this.close();
				    	}
				    },
				    {
				    	"text": MWF.APPFD.LP.button.cancel,
				    	"action": function(){
				    		this.close();
				    	}
				    }
				]
			});
			
			dlg.show();
		}.bind(this));
	},
	_insertCol: function(){
		var cols = $("MWFInsertColNumber").get("value");
		var positionRadios = document.getElementsByName("MWFInsertColPosition");
		var position = "before";
		for (var i=0; i<positionRadios.length; i++){
			if (positionRadios[i].checked){
				position = positionRadios[i].value;
				break;
			}
		}
		
		var tr = this.node.getParent("tr");
		var table = tr.getParent("table");
		
		var colIndex = this.node.cellIndex;
		var titleTr = table.rows[0];
		var dataTr = table.rows[1];

		var moduleList = [];
		
		var baseTh = titleTr.cells[colIndex];
		for (var m=1; m<=cols; m++){
			var newTh = new Element("th").inject(baseTh, position);
			this.form.getTemplateData("Datatable$Title", function(data){
				var moduleData = Object.clone(data);
				var thElement = new MWF.FCDatatable$Title(this.form);
				thElement.load(moduleData, newTh, this.parentContainer);
				moduleList.push(thElement);
				this.parentContainer.elements.push(thElement);
			}.bind(this));
		}
		
		var baseTd = dataTr.cells[colIndex];
		for (var n=1; n<=cols; n++){
			var newTd = new Element("td").inject(baseTd, position);
			this.form.getTemplateData("Datatable$Data", function(data){
				var moduleData = Object.clone(data);
				var tdContainer = new MWF.FCDatatable$Data(this.form);
				tdContainer.load(moduleData, newTd, this.parentContainer);
				moduleList.push(tdContainer);
				this.parentContainer.containers.push(tdContainer);
			}.bind(this));
		}
		
		this.unSelected();
		this.selected();

		this.addHistoryLog( "insertCol", moduleList );
	},
	
	deleteCol: function(e){
		var module = this;
		this.form.designer.confirm("warn", e, MWF.LP.process.notice.deleteColTitle, MWF.LP.process.notice.deleteCol, 300, 120, function(){

			var tr = module.node.getParent("tr");
			var table = tr.getParent("table");
			var colIndex = module.node.cellIndex;
			var titleTr = table.rows[0];
			var dataTr = table.rows[1];
			if (tr.cells.length<=1){
				module.parentContainer.addHistoryLog("delete");
			}else{
				var deleteTh = titleTr.cells[colIndex];
				var deleteTd = dataTr.cells[colIndex];
				var thModule = deleteTh.retrieve("module");
				var tdModule = deleteTd.retrieve("module");
				module.addHistoryLog("deleteCol", [thModule, tdModule]);
			}


			module._deleteCol();
			this.close();
		}, function(){
			this.close();
		}, null);
	},
	_deleteCol: function(){
		var tr = this.node.getParent("tr");
		var table = tr.getParent("table");
		var colIndex = this.node.cellIndex;
		
		var titleTr = table.rows[0];
		var dataTr = table.rows[1];

		this.unSelected();

		if (tr.cells.length<=1){
			this.parentContainer.destroy();
		}else{
			var deleteTh = titleTr.cells[colIndex];
			var deleteTd = dataTr.cells[colIndex];

			var thModule = deleteTh.retrieve("module");
			if (thModule){
				thModule.parentContainer.elements.erase(thModule);
				thModule.destroy();
			}
			
			var tdModule = deleteTd.retrieve("module");
			if (tdModule){
				tdModule.parentContainer.containers.erase(tdModule);
				tdModule.destroy();
			}
		}
	},
	_setEditStyle_custom: function(name){
		if (name=="name"){
			if (!this.json.name){
				this.node.set("text", "DataTitle");
			}else{
				this.node.set("text", this.json.name);
			}
			this.textNode = null;
			this.setPrefixOrSuffix();
		}
		if( name=="isShow" ){
			this._switchShow( true );
		}
		if (name=="prefixIcon" || name=="suffixIcon"){
			this.setPrefixOrSuffix();
		}
		// if (name=="styles"){
		// 	this.resetPrefixOrSuffix();
		// }
	},

	// resetPrefixOrSuffix: function(){
	// 	if (this.prefixNode){
	// 		var y = this.textNode.getSize().y;
	// 		this.prefixNode.setStyle("height", ""+y+"px");
	// 	}
	// 	if (this.suffixNode){
	// 		var y = this.textNode.getSize().y;
	// 		this.suffixNode.setStyle("height", ""+y+"px");
	// 	}
	// },
	getOffsetY: function(){
		return (this.node.getStyle("padding-top") || 0).toInt()
			+ (this.node.getStyle("padding-bottom") || 0).toInt()
			+ (this.node.getStyle("border-top") || 0).toInt()
			+ (this.node.getStyle("border-bottom") || 0).toInt();
	},
	setPrefixOrSuffix: function(){
		if (this.json.prefixIcon || this.json.suffixIcon){

			var lineheight = this.node.getStyle("line-height") || "28px";

			if (!this.textNode){
				var text = this.node.get("text");
				this.node.empty();
				this.wrapNode = new Element("div", {
					"styles": {
						"display": "flex",
						"align-items": "center",
						"justify-content": "center"
					}
				}).inject(this.node);

				if (this.json.prefixIcon){
					this.prefixNode = new Element("div", {"styles": {
						"width": "20px",
						"min-width": "20px",
						"height": lineheight,
						"background": "url("+this.json.prefixIcon+") center center no-repeat"
					}}).inject(this.wrapNode);
				}

				this.textNode = new Element("div", {"styles": {
						"line-height": lineheight,
						"vertical-align": "top",
						"padding": "1px"
					}, "text": text}).inject(this.wrapNode);

				if (this.json.suffixIcon){
					this.suffixNode = new Element("div", {"styles": {
						"width": "20px",
						"min-width": "20px",
						"height": lineheight,
						"background": "url("+this.json.suffixIcon+") center center no-repeat"
					}}).inject(this.wrapNode);
				}
			}else{
				if (this.json.prefixIcon){
					if (!this.prefixNode){
						this.prefixNode = new Element("div", {"styles": {
								"width": "20px",
								"min-width": "20px",
								"height": lineheight,
								"background": "url("+this.json.prefixIcon+") center center no-repeat"
							}}).inject(this.textNode, "before");
					}else{
						this.prefixNode.setStyle("background", "url("+this.json.prefixIcon+") center center no-repeat");
					}
				}else{
					if (this.prefixNode){
						this.prefixNode.destroy();
						this.prefixNode = null;
					}
				}
				if (this.json.suffixIcon){
					if (!this.suffixNode){
						this.suffixNode = new Element("div", {"styles": {
								"width": "20px",
								"min-width": "20px",
								"height": lineheight,
								"background": "url("+this.json.suffixIcon+") center center no-repeat"
							}}).inject(this.textNode, "after");
					}else{
						this.suffixNode.setStyle("background", "url("+this.json.suffixIcon+") center center no-repeat");
					}
				}else{
					if (this.suffixNode){
						this.suffixNode.destroy();
						this.suffixNode = null;
					}
				}
			}

		}else{
			//var text = this.textNode.get("text");
			this.node.empty();
			if (!this.json.name){
				this.node.set("text", "DataTitle");
			}else{
				this.node.set("text", this.json.name);
			}
			this.textNode = null;
			this.prefixNode = null;
			this.suffixNode = null;
		}
	},
	_switchShow: function( isChangeTd ){
		var tr = this.node.getParent("tr");
		var table = tr.getParent("table");
		var colIndex = this.node.cellIndex;
		var isShow = this.json.isShow !== false;

		var titleTr = table.rows[0];
		var currentTh = titleTr.cells[colIndex];
		if( currentTh ){
			currentTh.setStyle("opacity", isShow ? "1" : "0.3")
		}

		if(isChangeTd){
			var dataTr = table.rows[1];
			var currentTd = dataTr.cells[colIndex];
			if( currentTd ){
				var module = currentTd.retrieve("module");
				if( module )module._switchShow( isShow );
			}
		}
	}
});
