o2.widget = o2.widget || {};
//o2.require("o2.widget.ScriptEditor", null, false);
o2.require("o2.widget.JavascriptEditor", null, false);
o2.widget.ScriptArea = new Class({
    Implements: [Options, Events],
    Extends: o2.widget.Common,
    options: {
        "title": "ScriptArea",
        "style": "default",
        "helpStyle" : "default",
        "maxObj": document.body,
        "maxPosition": "relative",
        "isload": false,
        "isbind": true,
        "mode": "javascript",
        "runtime": "all",
        "key": "code",
        "forceType": null,
        "type": "web"   //"service" or "web"
    },
    initialize: function(node, options){
        this.setOptions(options);

        this.node = $(node);
        this.container = new Element("div");

        this.path = o2.session.path+"/widget/$ScriptArea/";
        this.cssPath = o2.session.path+"/widget/$ScriptArea/"+this.options.style+"/css.wcss";
        this._loadCss();
    },
    load: function(content){
        this.contentCode = content || {code: ''};
        if (this.fireEvent("queryLoad")){
            this.container.set("styles", this.css.container);
            this.container.inject(this.node);

            this.createTitleNode();
            this.createContent(content);

            this.fireEvent("postLoad");
        }
    },

    setData: function(data){
        this.contentCode = {code: data.code || data};
        if (this.jsEditor){
            this.jsEditor.setValue(data.code || data);
        }
    },
    createTitleNode: function(){
        this.titleNode = new Element("div", {
            "styles": this.css.titleNode,
            "events": {
                "dblclick": this.toggleSize.bind(this)
            }
        }).inject(this.container);

        this.titleActionNode = new Element("div", {
            "styles": this.css.titleActionNode,
            "events": {
                "click": this.toggleSize.bind(this)
            }
        }).inject(this.titleNode);
        this.titleActionNode.hide();

        this.referenceNode = new Element("div", {
            "styles": this.css.referenceNode,
            "title": "open API",
            "events": {
                "click": this.openApi.bind(this)
            }
        }).inject(this.titleNode);

        this.titleTextNode = new Element("div", {
            "styles": this.css.titleTextNode,
            "text": this.options.title
        }).inject(this.titleNode);
    },
    setTitle: function(title){
        if (this.titleTextNode){
            this.titleTextNode.set('text', title);
        }
    },
    openApi: function(){
        o2.openWindow(this.options.api || "../api")
    },
    toggleSize: function(e){
        var status = this.titleActionNode.retrieve("status", "max");
        if (status=="max"){
            this.maxSize();
        }else{
            this.returnSize();
        }
    },
    maxSize: function(){
        if (!this.jsEditor) return;
        var obj = this.options.maxObj;
        var coordinates = obj.getCoordinates(obj.getOffsetParent());

        this.container.store("size", {"height": this.container.getStyle("height"), "width": this.container.getStyle("width")});

        this.jsEditor.showLineNumbers();
        this.jsEditor.max();
        this.container.inject(obj, "top");
        this.container.setStyles({
            "position": this.options.maxPosition || "relative",
            // "top": coordinates.top,
            // "left": coordinates.left,
            //"top": coordinates.top+"px",
            //"left": coordinates.left+"px",
            //"width": coordinates.width,
            "width": "100%",
            "height": coordinates.height-2,
            "z-index": 20001
        });
        this.resizeContentNodeSize();
        this.titleActionNode.setStyle("background", "url("+this.path+this.options.style+"/icon/return.png) center center no-repeat");
        this.titleActionNode.store("status", "return");

        this.jsEditor.focus();
        this.fireEvent("maxSize");
    },

    returnSize: function(){
        var size = this.container.retrieve("size");

        //this.editor.setOption("lineNumbers", false);
        this.jsEditor.hideLineNumbers();
        this.container.inject(this.node);
        this.container.setStyles({
            "position": "static",
            "top": 0,
            "left": 0,
            "width": "auto",
            "height": size.height
        });

        this.resizeContentNodeSize();
        this.titleActionNode.setStyle("background", "url("+this.path+this.options.style+"/icon/max.png) center center no-repeat");
        this.titleActionNode.store("status", "max");

        this.jsEditor.focus();
        this.fireEvent("returnSize");
    },
    resizeContentNodeSize: function(){
        var titleSize = this.titleNode.getSize();
        var size = this.container.getSize();
        var h = this.container.getStyle("height").toInt();
        var th = this.titleNode.getStyle("height").toInt();
        var height = (size.y || h)-(titleSize.y || th)-2-6;
        this.contentNode.setStyle("height", ""+height+"px");
        if (this.jsEditor) this.jsEditor.resize();
    },
    toJson: function(){
        return (this.editor) ? {"code": this.editor.getValue(), "html": this.editor.getValue()} : this.contentCode;
    },
    getData: function(){
        return (this.editor) ? this.editor.getValue() : (this.contentCode.code || this.contentCode);
    },
    createContent: function(content){
        var codeIcon = (this.options.type!=="service") ? "web_code.png" : "code.png";
        var codeEmptyIcon = (this.options.type!=="service") ? "web_code_empty.png" : "code_empty.png";

        this.contentNode = new Element("div", {
            "styles": this.css.contentNode
        }).inject(this.container);

        this.resizeContentNodeSize();
        if (!content || !content.code){
            this.referenceNode.setStyle("background", "url("+o2.session.path+"/widget/$ScriptArea/"+this.options.style+"/icon/"+codeEmptyIcon+") center center no-repeat");
        }else{
            this.referenceNode.setStyle("background", "url("+o2.session.path+"/widget/$ScriptArea/"+this.options.style+"/icon/"+codeIcon+") center center no-repeat");
        }

        if (this.options.isload){
            this.loadEditor(content);
        }else{
            var inforNode = new Element("div", {"styles": this.css.inforNode, "text": o2.LP.widget.scriptAreaEditNotice}).inject(this.contentNode);
            var _self = this;
            inforNode.addEvent("click", function(){
                this.remove();
                _self.loadEditor(content);
            });
            this.inforNode = inforNode;
        }
    },

    bind: function(content){
        if( o2.typeOf(content) !== "object" )return;
        this.value = content.code;
        this.html = content.code;
        if (content.editors){
            content.editors.push(this.jsEditor);
        }else{
            Object.defineProperty(content, "editors", {
                configurable : false,
                enumerable : false,
                writable: true,
                "value": []
            });
            content.editors.push(this.jsEditor);

            Object.defineProperty(content, this.options.key, {
                configurable : true,
                enumerable : true,
                "get": function(){return this.value;}.bind(this),
                "set": function(v){
                    content.editors.each(function(editor){
                        if (editor.editor){
                            if (v!==editor.editor.getValue()) editor.editor.setValue(v,1);
                        }else{
                            editor.reload();
                        }
                    });
                    this.value = v;
                }.bind(this)
            });
        }
    },
    loadEditor: function(content){
        var value=(content) ? content.code : "";
        value = (value) ? value : "";
        this.jsEditor = new o2.widget.JavascriptEditor(this.contentNode,{
            "runtime": this.options.runtime || "all",
            "forceType": this.options.forceType,
            "option": {
                "value": value,
                "lineNumbers": false,
                "mode": this.options.mode
            },
            "onPostLoad": function(){
                this.editor = this.jsEditor.editor;

                this.jsEditor.addEditorEvent("change", function() {
                    this.fireEvent("change");
                }.bind(this));
                this.jsEditor.addEditorEvent("blur", function() {
                    this.fireEvent("blur");
                }.bind(this));

                this.jsEditor.resize();
                this.fireEvent("postLoadEditor");
            }.bind(this),
            "onSave": function(){
                this.fireEvent("change");
                this.fireEvent("save");
            }.bind(this)
        });
        this.jsEditor.load();
        if (this.options.isbind) this.bind(content);
        this.titleActionNode.show();

        var codeIcon = (this.options.type!=="service") ? "web_code.png" : "code.png";
        this.referenceNode.setStyle("background", "url("+o2.session.path+"/widget/$ScriptArea/"+this.options.style+"/icon/"+codeIcon+") center center no-repeat");
    },

    createScriptReferenceMenu: function(callback){
        o2.require("o2.widget.ScriptHelp", function(){
            this.scriptReferenceMenu = new o2.widget.ScriptHelp(this.referenceNode, this.jsEditor.editor, {
                "style" : this.options.helpStyle,
                "event": "click",
                "onPostLoad": function(){
                    if (callback) callback();
                }.bind(this)
            });
            this.scriptReferenceMenu.getEditor = function(){return this.jsEditor.editor;}.bind(this)
        }.bind(this));
    },
    showReferenceMenu: function(){
        var pos = this.jsEditor.getCursorPixelPosition();
        var e = {"page": {}};
        e.page.x = pos.left;
        e.page.y = pos.top;
        this.scriptReferenceMenu.menu.showIm(e);
    },
    focus: function(){
        if (this.jsEditor) this.jsEditor.focus();
    },
    destroy: function(){
        this.fireEvent("destroy");
        if (this.jsEditor){
            this.jsEditor.destroy();
        }
        this.container.destroy();
        o2.release(this);
    }
});




