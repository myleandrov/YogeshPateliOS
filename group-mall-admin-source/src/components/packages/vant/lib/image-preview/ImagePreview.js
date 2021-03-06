"use strict";

var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");

exports.__esModule = true;
exports.default = void 0;

var _utils = require("../utils");

var _number = require("../utils/format/number");

var _event = require("../utils/dom/event");

var _popup = require("../mixins/popup");

var _touch = require("../mixins/touch");

var _icon = _interopRequireDefault(require("../icon"));

var _image = _interopRequireDefault(require("../image"));

var _swipe = _interopRequireDefault(require("../swipe"));

var _loading = _interopRequireDefault(require("../loading"));

var _swipeItem = _interopRequireDefault(require("../swipe-item"));

// Utils
// Mixins
// Components
var _createNamespace = (0, _utils.createNamespace)('image-preview'),
    createComponent = _createNamespace[0],
    bem = _createNamespace[1];

function getDistance(touches) {
  return Math.sqrt(Math.pow(touches[0].clientX - touches[1].clientX, 2) + Math.pow(touches[0].clientY - touches[1].clientY, 2));
}

var _default2 = createComponent({
  mixins: [(0, _popup.PopupMixin)({
    skipToggleEvent: true
  }), _touch.TouchMixin],
  props: {
    className: null,
    asyncClose: Boolean,
    showIndicators: Boolean,
    images: {
      type: Array,
      default: function _default() {
        return [];
      }
    },
    loop: {
      type: Boolean,
      default: true
    },
    swipeDuration: {
      type: [Number, String],
      default: 500
    },
    overlay: {
      type: Boolean,
      default: true
    },
    showIndex: {
      type: Boolean,
      default: true
    },
    startPosition: {
      type: [Number, String],
      default: 0
    },
    minZoom: {
      type: [Number, String],
      default: 1 / 3
    },
    maxZoom: {
      type: [Number, String],
      default: 3
    },
    overlayClass: {
      type: String,
      default: bem('overlay')
    },
    closeable: Boolean,
    closeIcon: {
      type: String,
      default: 'clear'
    },
    closeIconPosition: {
      type: String,
      default: 'top-right'
    }
  },
  data: function data() {
    return {
      scale: 1,
      moveX: 0,
      moveY: 0,
      active: 0,
      moving: false,
      zooming: false,
      doubleClickTimer: null
    };
  },
  computed: {
    imageStyle: function imageStyle() {
      var scale = this.scale;
      var style = {
        transitionDuration: this.zooming || this.moving ? '0s' : '.3s'
      };

      if (scale !== 1) {
        style.transform = "scale3d(" + scale + ", " + scale + ", 1) translate(" + this.moveX / scale + "px, " + this.moveY / scale + "px)";
      }

      return style;
    }
  },
  watch: {
    startPosition: 'setActive',
    value: function value(val) {
      var _this = this;

      if (val) {
        this.setActive(+this.startPosition);
        this.$nextTick(function () {
          _this.$refs.swipe.swipeTo(+_this.startPosition, {
            immediate: true
          });
        });
      } else {
        this.$emit('close', {
          index: this.active,
          url: this.images[this.active]
        });
      }
    },
    shouldRender: {
      handler: function handler(val) {
        var _this2 = this;

        if (val) {
          this.$nextTick(function () {
            var swipe = _this2.$refs.swipe.$el;
            (0, _event.on)(swipe, 'touchstart', _this2.onWrapperTouchStart);
            (0, _event.on)(swipe, 'touchmove', _event.preventDefault);
            (0, _event.on)(swipe, 'touchend', _this2.onWrapperTouchEnd);
            (0, _event.on)(swipe, 'touchcancel', _this2.onWrapperTouchEnd);
          });
        }
      },
      immediate: true
    }
  },
  methods: {
    emitClose: function emitClose() {
      if (!this.asyncClose) {
        this.$emit('input', false);
      }
    },
    onWrapperTouchStart: function onWrapperTouchStart() {
      this.touchStartTime = new Date();
    },
    onWrapperTouchEnd: function onWrapperTouchEnd(event) {
      var _this3 = this;

      (0, _event.preventDefault)(event);
      var deltaTime = new Date() - this.touchStartTime;

      var _ref = this.$refs.swipe || {},
          _ref$offsetX = _ref.offsetX,
          offsetX = _ref$offsetX === void 0 ? 0 : _ref$offsetX,
          _ref$offsetY = _ref.offsetY,
          offsetY = _ref$offsetY === void 0 ? 0 : _ref$offsetY; // prevent long tap to close component


      if (deltaTime < 300 && offsetX < 10 && offsetY < 10) {
        if (!this.doubleClickTimer) {
          this.doubleClickTimer = setTimeout(function () {
            _this3.emitClose();

            _this3.doubleClickTimer = null;
          }, 300);
        } else {
          clearTimeout(this.doubleClickTimer);
          this.doubleClickTimer = null;
          this.toggleScale();
        }
      }
    },
    startMove: function startMove(event) {
      var image = event.currentTarget;
      var rect = image.getBoundingClientRect();
      var winWidth = window.innerWidth;
      var winHeight = window.innerHeight;
      this.touchStart(event);
      this.moving = true;
      this.startMoveX = this.moveX;
      this.startMoveY = this.moveY;
      this.maxMoveX = Math.max(0, (rect.width - winWidth) / 2);
      this.maxMoveY = Math.max(0, (rect.height - winHeight) / 2);
    },
    startZoom: function startZoom(event) {
      this.moving = false;
      this.zooming = true;
      this.startScale = this.scale;
      this.startDistance = getDistance(event.touches);
    },
    onImageTouchStart: function onImageTouchStart(event) {
      var touches = event.touches;

      var _ref2 = this.$refs.swipe || {},
          _ref2$offsetX = _ref2.offsetX,
          offsetX = _ref2$offsetX === void 0 ? 0 : _ref2$offsetX;

      if (touches.length === 1 && this.scale !== 1) {
        this.startMove(event);
      }
      /* istanbul ignore else */
      else if (touches.length === 2 && !offsetX) {
          this.startZoom(event);
        }
    },
    onImageTouchMove: function onImageTouchMove(event) {
      var touches = event.touches;

      if (this.moving || this.zooming) {
        (0, _event.preventDefault)(event, true);
      }

      if (this.moving) {
        this.touchMove(event);
        var moveX = this.deltaX + this.startMoveX;
        var moveY = this.deltaY + this.startMoveY;
        this.moveX = (0, _number.range)(moveX, -this.maxMoveX, this.maxMoveX);
        this.moveY = (0, _number.range)(moveY, -this.maxMoveY, this.maxMoveY);
      }

      if (this.zooming && touches.length === 2) {
        var distance = getDistance(touches);
        var scale = this.startScale * distance / this.startDistance;
        this.setScale(scale);
      }
    },
    onImageTouchEnd: function onImageTouchEnd(event) {
      /* istanbul ignore else */
      if (this.moving || this.zooming) {
        var stopPropagation = true;

        if (this.moving && this.startMoveX === this.moveX && this.startMoveY === this.moveY) {
          stopPropagation = false;
        }

        if (!event.touches.length) {
          this.moving = false;
          this.zooming = false;
          this.startMoveX = 0;
          this.startMoveY = 0;
          this.startScale = 1;

          if (this.scale < 1) {
            this.resetScale();
          }
        }

        if (stopPropagation) {
          (0, _event.preventDefault)(event, true);
        }
      }
    },
    setActive: function setActive(active) {
      this.resetScale();

      if (active !== this.active) {
        this.active = active;
        this.$emit('change', active);
      }
    },
    setScale: function setScale(scale) {
      var value = (0, _number.range)(scale, +this.minZoom, +this.maxZoom);
      this.scale = value;
      this.$emit('scale', {
        index: this.active,
        scale: value
      });
    },
    resetScale: function resetScale() {
      this.setScale(1);
      this.moveX = 0;
      this.moveY = 0;
    },
    toggleScale: function toggleScale() {
      var scale = this.scale > 1 ? 1 : 2;
      this.setScale(scale);
      this.moveX = 0;
      this.moveY = 0;
    },
    genIndex: function genIndex() {
      var h = this.$createElement;

      if (this.showIndex) {
        return h("div", {
          "class": bem('index')
        }, [this.slots('index') || this.active + 1 + " / " + this.images.length]);
      }
    },
    genCover: function genCover() {
      var h = this.$createElement;
      var cover = this.slots('cover');

      if (cover) {
        return h("div", {
          "class": bem('cover')
        }, [cover]);
      }
    },
    genImages: function genImages() {
      var _this4 = this;

      var h = this.$createElement;
      var imageSlots = {
        loading: function loading() {
          return h(_loading.default, {
            "attrs": {
              "type": "spinner"
            }
          });
        }
      };
      return h(_swipe.default, {
        "ref": "swipe",
        "attrs": {
          "lazyRender": true,
          "loop": this.loop,
          "indicatorColor": "white",
          "duration": this.swipeDuration,
          "initialSwipe": this.startPosition,
          "showIndicators": this.showIndicators
        },
        "class": bem('swipe'),
        "on": {
          "change": this.setActive
        }
      }, [this.images.map(function (image, index) {
        return h(_swipeItem.default, [h(_image.default, {
          "attrs": {
            "src": image,
            "fit": "contain"
          },
          "class": bem('image'),
          "scopedSlots": imageSlots,
          "style": index === _this4.active ? _this4.imageStyle : null,
          "nativeOn": {
            "touchstart": _this4.onImageTouchStart,
            "touchmove": _this4.onImageTouchMove,
            "touchend": _this4.onImageTouchEnd,
            "touchcancel": _this4.onImageTouchEnd
          }
        })]);
      })]);
    },
    genClose: function genClose() {
      var h = this.$createElement;

      if (this.closeable) {
        return h(_icon.default, {
          "attrs": {
            "role": "button",
            "name": this.closeIcon
          },
          "class": bem('close-icon', this.closeIconPosition),
          "on": {
            "click": this.emitClose
          }
        });
      }
    },
    onClosed: function onClosed() {
      this.$emit('closed');
    }
  },
  render: function render() {
    var h = arguments[0];

    if (!this.shouldRender) {
      return;
    }

    return h("transition", {
      "attrs": {
        "name": "van-fade"
      },
      "on": {
        "afterLeave": this.onClosed
      }
    }, [h("div", {
      "directives": [{
        name: "show",
        value: this.value
      }],
      "class": [bem(), this.className]
    }, [this.genClose(), this.genImages(), this.genIndex(), this.genCover()])]);
  }
});

exports.default = _default2;