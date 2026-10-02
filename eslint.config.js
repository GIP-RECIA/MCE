import antfu from '@antfu/eslint-config'

export default antfu({
  typescript: true,
  formatters: {
    css: true,
    html: true,
    prettierOptions: {
      singleAttributePerLine: true,
    },
  },
  vue: {
    overrides: {
      'vue/max-attributes-per-line': [
        'error',
        {
          singleline: 1,
          multiline: 1,
        },
      ],
    },
  },
  ignores: [
    './src/main/resources/config',
  ],
})
