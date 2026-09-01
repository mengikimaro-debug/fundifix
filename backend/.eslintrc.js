module.exports = {
	env: {
		node: true,
		es2021: true
	},
	extends: 'eslint:recommended',
	parserOptions: {
		ecmaVersion: 'latest'
	},
	rules: {
		'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
		'no-unused-vars': 'warn',
		'no-trailing-spaces': 'error',
		'indent': ['error', 'tab'],
		'quotes': ['error', 'single'],
		'semi': ['error', 'always'],
		'eqeqeq': ['error', 'always']
	}
};
